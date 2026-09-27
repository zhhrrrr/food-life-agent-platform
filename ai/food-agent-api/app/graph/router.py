import json
import logging
import re
from collections.abc import Sequence
from functools import lru_cache
from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.graph.semantic_router import SemanticRouter
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model
from app.llm.structured_output import invoke_structured_output
from app.memory.conversation_memory import (
    ConversationTurn,
    should_use_conversation_history,
)
from app.trade.policy import detect_trade_mutation

AgentName = Literal["recommend", "trade", "support"]
RouterSource = Literal["semantic", "llm_tool", "llm_structured", "rules"]

logger = logging.getLogger("food_agent.graph.router")

ROUTE_TOOL_BY_INTENT: dict[AgentName, str] = {
    "recommend": "route_recommend",
    "trade": "route_trade",
    "support": "route_support",
}
INTENT_BY_ROUTE_TOOL = {tool: intent for intent, tool in ROUTE_TOOL_BY_INTENT.items()}


def _route_tool_parameters() -> dict[str, object]:
    return {
        "type": "object",
        "properties": {
            "confidence": {
                "type": "number",
                "minimum": 0,
                "maximum": 1,
                "description": "Confidence that this is the correct route.",
            },
            "reason": {
                "type": "string",
                "minLength": 1,
                "description": "Short reason for selecting this route.",
            },
        },
        "required": ["confidence", "reason"],
        "additionalProperties": False,
    }


ROUTING_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "route_recommend",
            "description": (
                "Route requests for shop or package recommendations, cuisine/budget/scene filters, "
                "reviews, comparisons, and discovery."
            ),
            "parameters": _route_tool_parameters(),
        },
    },
    {
        "type": "function",
        "function": {
            "name": "route_trade",
            "description": (
                "Route real-time price, stock, coupon, group-buy, order, or order-preview queries, "
                "including any request with a concrete order/package/team ID that asks for its current data, "
                "and transaction-action requests that TradeAgent must safely accept or reject."
            ),
            "parameters": _route_tool_parameters(),
        },
    },
    {
        "type": "function",
        "function": {
            "name": "route_support",
            "description": (
                "Route platform rules, refund/payment/group-buy explanations, after-sales help, "
                "generic order-status meaning, and FAQ questions. Do not use this route to query "
                "the current data of a concrete order ID."
            ),
            "parameters": _route_tool_parameters(),
        },
    },
]


class IntentResult(BaseModel):
    intent: AgentName
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str
    source: RouterSource = "rules"
    tool: str = ""
    semantic_score: float | None = None
    semantic_margin: float | None = None


TRADE_KEYWORDS = (
    "\u8ba2\u5355",
    "\u5e93\u5b58",
    "\u4f18\u60e0\u5238",
    "\u4ef7\u683c",
    "\u62fc\u56e2",
    "\u53d6\u6d88",
)
SUPPORT_KEYWORDS = (
    "\u9000\u6b3e\u89c4\u5219",
    "\u62fc\u56e2\u89c4\u5219",
    "\u652f\u4ed8\u89c4\u5219",
    "\u9000\u6b3e",
    "\u552e\u540e",
    "\u89c4\u5219",
    "\u652f\u4ed8",
    "FAQ",
    "faq",
)
ORDER_ID_PATTERN = re.compile(
    r"(?:订单(?:号|ID|id|编号)?|order(?:\s*id|\s*no)?)\s*[:：#]?\s*\d{1,19}",
    re.IGNORECASE,
)
ORDER_STATUS_EXPLANATION_MARKERS = (
    "是什么意思",
    "代表什么",
    "怎么理解",
    "怎么解释",
    "状态说明",
    "有哪些状态",
)
ORDER_QUERY_MARKERS = (
    "查询",
    "我的订单",
    "当前",
    "现在",
    "订单详情",
    "订单进度",
    "到哪一步",
    "怎么样",
)


# 按照“语义路由 → LLM Tool Calling → LLM 结构化输出 → 规则兜底”的顺序，最终判断用户属于 recommend / trade / support 哪个意图。
def route_intent(
    message: str,
    settings: Settings | None = None,
    chat_model: object | None = None,
    semantic_router: SemanticRouter | None = None,
    conversation_history: list[ConversationTurn] | None = None,
) -> IntentResult:
    resolved_settings = settings or get_settings()
    history = conversation_history or []
    fallback = route_intent_by_rules(_rule_query(message, history))
    semantic_match = (semantic_router or _resolve_semantic_router(settings)).match(
        message, history
    )

    # 分两个支路走
    if semantic_match is not None:
        logger.info(
            "semantic_route intent=%s score=%.4f margin=%.4f accepted=%s",
            semantic_match.intent,
            semantic_match.score,
            semantic_match.margin,
            semantic_match.accepted,
        )
        if semantic_match.accepted:
            return IntentResult(
                intent=semantic_match.intent,
                confidence=semantic_match.score,
                reason=f"semantic match: {semantic_match.matched_example}",
                source="semantic",
                tool=ROUTE_TOOL_BY_INTENT[semantic_match.intent],
                semantic_score=semantic_match.score,
                semantic_margin=semantic_match.margin,
            )

    if not resolved_settings.llm_router_enabled:
        return fallback

    messages = _tool_router_messages(message, history)
    try:
        return _invoke_tool_router(
            settings=resolved_settings,
            messages=messages,
            chat_model=chat_model,
        )
    except ChatModelUnavailableError as exc:
        logger.info("llm_tool_router_fallback reason=%s", exc)
    except Exception as exc:
        logger.warning("llm_tool_router_failed error=%s", type(exc).__name__)

    # Tool Calling 失败后，尝试 LLM 结构化输出
    try:
        result = invoke_structured_output(
            settings=resolved_settings,
            schema=IntentResult,
            messages=_structured_router_messages(message, history),
            chat_model=chat_model,
        )
        return result.model_copy(
            update={
                "source": "llm_structured",
                "tool": ROUTE_TOOL_BY_INTENT[result.intent],
            }
        )
    except ChatModelUnavailableError as exc:
        logger.info("llm_structured_router_fallback reason=%s", exc)
        return fallback
    except Exception as exc:
        logger.warning("llm_structured_router_failed error=%s", type(exc).__name__)
        return fallback


def route_intent_by_rules(message: str) -> IntentResult:
    if detect_trade_mutation(message):
        return _rule_result("trade", 0.65, "fallback rule matched trade action keyword")
    if _is_order_status_explanation(message):
        return _rule_result("support", 0.65, "fallback rule matched status explanation")
    if _is_current_order_query(message):
        return _rule_result("trade", 0.65, "fallback rule matched current order query")
    if any(keyword in message for keyword in SUPPORT_KEYWORDS):
        return _rule_result("support", 0.65, "fallback rule matched support keyword")
    if any(keyword in message for keyword in TRADE_KEYWORDS):
        return _rule_result("trade", 0.65, "fallback rule matched trade keyword")
    return _rule_result("recommend", 0.55, "fallback rule defaulted to recommend")


def _is_order_status_explanation(message: str) -> bool:
    return "订单" in message and any(
        marker in message for marker in ORDER_STATUS_EXPLANATION_MARKERS
    )


def _is_current_order_query(message: str) -> bool:
    if "订单" not in message.lower() and "order" not in message.lower():
        return False
    return bool(ORDER_ID_PATTERN.search(message)) or any(
        marker in message for marker in ORDER_QUERY_MARKERS
    )


def _invoke_tool_router(
    *,
    settings: Settings,
    messages: Sequence[tuple[str, str]],
    chat_model: object | None,
) -> IntentResult:
    model = chat_model or build_chat_model(settings)
    if not hasattr(model, "bind_tools"):
        raise ChatModelUnavailableError("chat model does not support tool calling")
    # model.bind_tools(...) 通常就是 LangChain ChatModel 提供的方法
    bound_model = model.bind_tools(ROUTING_TOOLS, tool_choice="required")
    response = bound_model.invoke(list(messages))
    tool_calls = getattr(response, "tool_calls", None) or []
    if len(tool_calls) != 1:
        raise ValueError("LLM router must return exactly one tool call")

    tool_call = tool_calls[0]
    tool_name = str(tool_call.get("name") or "")
    intent = INTENT_BY_ROUTE_TOOL.get(tool_name)
    if intent is None:
        raise ValueError(f"unsupported routing tool: {tool_name}")
    arguments = tool_call.get("args") or {}
    if isinstance(arguments, str):
        arguments = json.loads(arguments)
    if not isinstance(arguments, dict):
        raise ValueError("routing tool arguments must be an object")

    return IntentResult(
        intent=intent,
        confidence=arguments.get("confidence"),
        reason=arguments.get("reason"),
        source="llm_tool",
        tool=tool_name,
    )


def _rule_result(intent: AgentName, confidence: float, reason: str) -> IntentResult:
    return IntentResult(
        intent=intent,
        confidence=confidence,
        reason=reason,
        source="rules",
        tool=ROUTE_TOOL_BY_INTENT[intent],
    )


def _tool_router_messages(
    message: str,
    history: list[ConversationTurn],
) -> list[tuple[str, str]]:
    messages: list[tuple[str, str]] = [
        (
            "system",
            (
                "You are the intent router for a food-life business agent. "
                "Call exactly one supplied routing tool. Never answer the user and never call a business API. "
                "Use recommend for discovery and recommendations; trade for current business facts or "
                "transaction requests; support for policies, status meanings, after-sales guidance, and FAQ. "
                "A concrete order/package/team ID plus a request for current detail or status always routes "
                "to trade. Support handles what a status means in general, not the current state of an ID. "
                "Use conversation history only to resolve the current request. "
                "Provide confidence from 0 to 1 and a short reason in the tool arguments."
            ),
        )
    ]
    for turn in history[-4:]:
        messages.append(("human", turn.user[:500]))
        if turn.assistant:
            messages.append(("assistant", turn.assistant[:500]))
    messages.append(("human", message))
    return messages


def _structured_router_messages(
    message: str,
    history: list[ConversationTurn],
) -> list[tuple[str, str]]:
    messages: list[tuple[str, str]] = [
        (
            "system",
            (
                "Classify the current food-life request as recommend, trade, or support. "
                "A concrete order/package/team ID plus current detail or status is trade. "
                "A generic policy or status-meaning question is support. "
                "Return only a JSON object with intent, confidence, and reason."
            ),
        )
    ]
    for turn in history[-4:]:
        messages.append(("human", turn.user[:500]))
        if turn.assistant:
            messages.append(("assistant", turn.assistant[:500]))
    messages.append(("human", message))
    return messages


def _rule_query(message: str, history: list[ConversationTurn]) -> str:
    if not history or not should_use_conversation_history(message):
        return message
    return f"{history[-1].user}\n{message}"


@lru_cache(maxsize=1)
def _default_semantic_router() -> SemanticRouter:
    return SemanticRouter(settings=get_settings())


def _resolve_semantic_router(settings: Settings | None) -> SemanticRouter:
    if settings is None:
        return _default_semantic_router()
    return SemanticRouter(settings=settings)

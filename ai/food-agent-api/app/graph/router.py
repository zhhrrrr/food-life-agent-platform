import logging
from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model

AgentName = Literal["recommend", "trade", "support"]

logger = logging.getLogger("food_agent.graph.router")


class IntentResult(BaseModel):
    intent: AgentName
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str


TRADE_KEYWORDS = (
    "\u8ba2\u5355",
    "\u4e0b\u5355",
    "\u5e93\u5b58",
    "\u4f18\u60e0\u5238",
    "\u4ef7\u683c",
    "\u62fc\u56e2",
    "\u53d6\u6d88",
)
SUPPORT_KEYWORDS = (
    "\u9000\u6b3e",
    "\u552e\u540e",
    "\u89c4\u5219",
    "\u652f\u4ed8",
    "FAQ",
    "faq",
)


def route_intent(
    message: str,
    settings: Settings | None = None,
    chat_model: object | None = None,
) -> IntentResult:
    resolved_settings = settings or get_settings()
    fallback = route_intent_by_rules(message)

    if not resolved_settings.llm_router_enabled:
        return fallback

    try:
        model = chat_model or build_chat_model(resolved_settings)
        structured_kwargs = _structured_output_kwargs(resolved_settings)
        structured_model = model.with_structured_output(  # type: ignore[attr-defined]
            IntentResult,
            **structured_kwargs,
        )
        result = structured_model.invoke(_router_messages(message))
    except ChatModelUnavailableError as exc:
        logger.info("llm_router_fallback reason=%s", exc)
        return fallback
    except Exception:
        logger.exception("llm_router_failed")
        return fallback

    if isinstance(result, IntentResult):
        return result
    try:
        return IntentResult.model_validate(result)
    except Exception:
        logger.exception("llm_router_invalid_result")
        return fallback


def _structured_output_kwargs(settings: Settings) -> dict[str, str]:
    if settings.llm_provider.strip().lower() == "zhipu":
        return {"method": "json_mode"}
    return {}


def route_intent_by_rules(message: str) -> IntentResult:
    if any(keyword in message for keyword in TRADE_KEYWORDS):
        return IntentResult(
            intent="trade",
            confidence=0.65,
            reason="fallback rule matched trade keyword",
        )
    if any(keyword in message for keyword in SUPPORT_KEYWORDS):
        return IntentResult(
            intent="support",
            confidence=0.65,
            reason="fallback rule matched support keyword",
        )
    return IntentResult(
        intent="recommend",
        confidence=0.55,
        reason="fallback rule defaulted to recommend",
    )


def _router_messages(message: str) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You are the intent router for a food-life business agent. "
                "Classify the user message into exactly one intent: "
                "recommend for shop/package/activity/coupon recommendations, "
                "trade for order, payment, stock, coupon transaction, cancellation, "
                "group-buy or seckill operation requests, "
                "support for platform rules, refund, after-sales, FAQ, policy, or help questions. "
                "Return only one valid JSON object, without markdown fences or extra text. "
                "The JSON object must include all fields: "
                'intent, confidence, reason. Example: {"intent":"recommend",'
                '"confidence":0.86,"reason":"The user asks for restaurant suggestions."}'
            ),
        ),
        ("human", message),
    ]

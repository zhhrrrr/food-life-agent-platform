from __future__ import annotations

import logging
import re
from typing import Literal, TypedDict

from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.llm.structured_output import invoke_structured_output_with_fallback

logger = logging.getLogger("food_agent.agents.general")


class GeneralAgentInput(TypedDict, total=False):
    message: str
    session_history: list[object]


class GeneralAnswer(BaseModel):
    response_type: Literal["chat", "clarify", "capability_guide", "out_of_scope"]
    response: str = Field(min_length=1, max_length=1500)


def general_agent_node(state: GeneralAgentInput) -> dict[str, object]:
    message = state["message"].strip()
    answer = simple_general_answer(message)
    if answer is None:
        answer = _generate_answer(message, state.get("session_history", []))
    return {
        "response": answer.response,
        "rag_mode": f"general_agent_{answer.response_type}",
        "query": message,
        "rewritten_query": message,
        "capabilities": [answer.response_type],
        "context": [],
        "references": [],
    }


def _generate_answer(message: str, history: list[object]) -> GeneralAnswer:
    from app.memory.query_context import history_messages

    return invoke_structured_output_with_fallback(
        settings=get_settings(),
        schema=GeneralAnswer,
        messages=[
            (
                "system",
                (
                    "You are the lightweight receptionist for a food-life assistant. "
                    "Handle greetings, capability guidance, harmless casual chat, and clarification. "
                    "You have no tools and must not claim current prices, stock, coupons, orders, "
                    "payments, refunds, or external real-time facts. Keep the answer concise."
                ),
            ),
            *history_messages(history, message=message),
            ("human", message),
        ],
        fallback=_fallback_answer(message),
        operation="general_agent_answer",
        logger=logger,
    )


def simple_general_answer(message: str) -> GeneralAnswer | None:
    normalized = re.sub(r"[\s，。！？,.!?]+", "", message).casefold()
    tokens = ("你好", "您好", "谢谢", "感谢", "你是谁", "你能做什么", "能做什么", "有什么功能", "支持什么", "hello", "hi")
    if not normalized or not re.fullmatch("(?:" + "|".join(tokens) + ")+", normalized):
        return None
    if any(token in normalized for token in ("你是谁", "能做什么", "有什么功能", "支持什么")):
        return GeneralAnswer(
            response_type="capability_guide",
            response="我可以协助美食推荐、平台规则说明，以及价格、库存、优惠券和订单等只读查询。",
        )
    if any(token in normalized for token in ("谢谢", "感谢")):
        return GeneralAnswer(response_type="chat", response="不客气，有需要随时告诉我。")
    return GeneralAnswer(response_type="chat", response="你好，有什么可以帮你的？")


def _fallback_answer(message: str) -> GeneralAnswer:
    simple = simple_general_answer(message)
    if simple is not None:
        return simple
    if len(message) <= 8 and any(token in message for token in ("这个", "那个", "看看", "查一下")):
        return GeneralAnswer(
            response_type="clarify",
            response="请补充要查询的商家、套餐或订单信息，我再继续处理。",
        )
    return GeneralAnswer(
        response_type="out_of_scope",
        response="这个问题暂不属于当前美食推荐、平台规则或交易查询范围。",
    )

from __future__ import annotations

import logging
from typing import Literal, TypedDict

from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.llm.structured_output import invoke_structured_output_with_fallback

logger = logging.getLogger("food_agent.agents.general")


class GeneralAgentInput(TypedDict, total=False):
    message: str
    conversation_history: list[object]


class GeneralAnswer(BaseModel):
    response_type: Literal["chat", "clarify", "capability_guide", "out_of_scope"]
    response: str = Field(min_length=1, max_length=1500)


def general_agent_node(state: GeneralAgentInput) -> dict[str, object]:
    message = state["message"].strip()
    fallback = _fallback_answer(message)
    settings = get_settings()
    answer = invoke_structured_output_with_fallback(
        settings=settings,
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
            ("human", message),
        ],
        fallback=fallback,
        operation="general_agent_answer",
        logger=logger,
    )
    return {
        "response": answer.response,
        "rag_mode": f"general_agent_{answer.response_type}",
        "query": message,
        "rewritten_query": message,
        "capabilities": [answer.response_type],
        "context": [],
        "references": [],
    }


def _fallback_answer(message: str) -> GeneralAnswer:
    lowered = message.casefold()
    if any(token in lowered for token in ("你好", "您好", "hello", "hi")):
        return GeneralAnswer(response_type="chat", response="你好，有什么可以帮你的？")
    if any(token in message for token in ("能做什么", "有什么功能", "支持什么")):
        return GeneralAnswer(
            response_type="capability_guide",
            response="我可以协助美食推荐、平台规则说明，以及价格、库存、优惠券和订单等只读查询。",
        )
    if len(message) <= 8 and any(token in message for token in ("这个", "那个", "看看", "查一下")):
        return GeneralAnswer(
            response_type="clarify",
            response="请补充要查询的商家、套餐或订单信息，我再继续处理。",
        )
    return GeneralAnswer(
        response_type="out_of_scope",
        response="这个问题暂不属于当前美食推荐、平台规则或交易查询范围。",
    )

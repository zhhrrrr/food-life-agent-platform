import json
import logging
import re
from typing import Any, Literal

from pydantic import Field

from app.core.config import Settings, get_settings
from app.llm.structured_output import invoke_structured_output_with_fallback
from app.rag.chain_models import (
    AgentChainResult,
    QueryRewriteBase,
    invoke_grounded_answer,
    merge_capabilities,
    select_references,
)
from app.search.schemas import Reference
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient
from app.trade.policy import detect_trade_mutation

logger = logging.getLogger("food_agent.trade.chain")

TradeCapability = Literal[
    "price_query",
    "stock_query",
    "coupon_query",
    "group_buy_query",
    "order_query",
    "order_preview",
]

_PACKAGE_ID_PATTERNS = (
    re.compile(
        r"(?:套餐(?:ID|id|编号|号)?|package(?:\s*id)?)\s*[:：#]?\s*(\d{1,19})",
        re.IGNORECASE,
    ),
)
_ORDER_ID_PATTERNS = (
    re.compile(
        r"(?:订单(?:ID|id|编号|号)?|order(?:\s*id|\s*no)?)\s*[:：#]?\s*(\d{1,19})",
        re.IGNORECASE,
    ),
    re.compile(r"#(\d{1,19})"),
)
_TEAM_ID_PATTERNS = (
    re.compile(
        r"(?:拼团(?:队伍)?(?:ID|id|编号|号)|team(?:\s*id)?)\s*[:：#]?\s*([A-Za-z0-9_-]{1,64})",
        re.IGNORECASE,
    ),
)
_QUANTITY_PATTERNS = (
    re.compile(r"(?:数量|份数)\s*[:：]?\s*(\d{1,3})"),
    re.compile(r"(\d{1,3})\s*份"),
)


class TradeQueryRewrite(QueryRewriteBase):
    capabilities: list[TradeCapability] = Field(min_length=1)


class TradeChainResult(AgentChainResult):
    facts: list[dict[str, object]] = Field(default_factory=list)
    query_statuses: list[dict[str, object]] = Field(default_factory=list)
    operation_executed: bool = False


class TradeChain:
    def __init__(
        self,
        settings: Settings | None = None,
        gateway_client: TradeGatewayClient | None = None,
        chat_model: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.gateway_client = gateway_client or TradeGatewayClient(
            settings=self.settings
        )
        self.chat_model = chat_model

    def run(self, message: str, authorization: str = "") -> TradeChainResult:
        rewrite = self.rewrite_query(message)
        blocked_action = detect_trade_mutation(message)
        if blocked_action:
            return TradeChainResult(
                response=(
                    f"当前 TradeAgent 只支持查询，不能执行{blocked_action}。"
                    "本次没有调用任何交易写接口。"
                ),
                rag_mode="trade_agent_java_query",
                query=message,
                rewritten_query=rewrite.search_query,
                capabilities=list(rewrite.capabilities),
                query_statuses=[
                    {
                        "status": "blocked_operation",
                        "operation": blocked_action,
                    }
                ],
            )

        query_results = self._query_facts(message, rewrite, authorization)
        successful_results = [result for result in query_results if result.has_data]
        if not successful_results:
            return TradeChainResult(
                response=_no_facts_message(query_results),
                rag_mode="trade_agent_java_query",
                query=message,
                rewritten_query=rewrite.search_query,
                capabilities=list(rewrite.capabilities),
                query_statuses=[
                    result.model_dump(exclude={"data"}) for result in query_results
                ],
            )

        facts = _facts(successful_results)
        context = _context_summary(facts)
        references = _references(successful_results)
        generation = invoke_grounded_answer(
            settings=self.settings,
            messages=_answer_messages(message, rewrite, context),
            fallback_answer=_template_answer(context),
            reference_ids=[reference.id for reference in references],
            operation="trade_answer",
            logger=logger,
            chat_model=self.chat_model,
        )
        answer = generation.answer
        reference_dicts = [reference.model_dump() for reference in references]
        return TradeChainResult(
            response=answer.answer,
            rag_mode="trade_agent_java_query",
            query=message,
            rewritten_query=rewrite.search_query,
            capabilities=list(rewrite.capabilities),
            context=facts,
            references=select_references(reference_dicts, answer.used_reference_ids),
            facts=facts,
            query_statuses=[
                result.model_dump(exclude={"data"}) for result in query_results
            ],
        )

    def rewrite_query(self, message: str) -> TradeQueryRewrite:
        fallback = _fallback_rewrite(message)
        result = invoke_structured_output_with_fallback(
            settings=self.settings,
            schema=TradeQueryRewrite,
            messages=_rewrite_messages(message),
            fallback=fallback,
            operation="trade_query_rewrite",
            logger=logger,
            chat_model=self.chat_model,
        )

        return result.model_copy(
            update={
                "capabilities": merge_capabilities(
                    list(result.capabilities),
                    list(fallback.capabilities),
                )
            }
        )

    def _query_facts(
        self,
        message: str,
        rewrite: TradeQueryRewrite,
        authorization: str,
    ) -> list[GatewayQueryResult]:
        capabilities = set(rewrite.capabilities)
        package_id = _extract_int(message, _PACKAGE_ID_PATTERNS)
        order_id = _extract_int(message, _ORDER_ID_PATTERNS)
        team_id = _extract_text(message, _TEAM_ID_PATTERNS)
        quantity = _extract_quantity(message)
        results: list[GatewayQueryResult] = []

        package_result: GatewayQueryResult | None = None
        if capabilities.intersection({"price_query", "stock_query", "order_preview"}):
            if package_id is None:
                package_result = _missing_parameter(
                    "package_snapshot",
                    "价格、库存或订单预览查询需要明确的套餐 ID。",
                )
            else:
                package_result = self.gateway_client.get_package_snapshot(package_id)
            results.append(package_result)

        coupon_result: GatewayQueryResult | None = None
        if "coupon_query" in capabilities or (
            "order_preview" in capabilities and authorization.strip()
        ):
            if _asks_for_my_coupons(message) or "order_preview" in capabilities:
                coupon_result = self.gateway_client.list_my_coupons(authorization)
            else:
                coupon_result = self.gateway_client.list_coupon_templates(authorization)
            results.append(coupon_result)

        if "group_buy_query" in capabilities:
            if _asks_for_my_group_buys(message):
                results.append(
                    self.gateway_client.list_my_group_buy_orders(authorization)
                )
            elif team_id:
                results.append(
                    self.gateway_client.get_group_buy_team(team_id, authorization)
                )
            elif package_id is not None:
                results.append(
                    self.gateway_client.list_group_buy_teams(package_id, authorization)
                )
            else:
                results.append(
                    _missing_parameter(
                        "group_buy_query",
                        "拼团查询需要套餐 ID、拼团队伍 ID，或明确查询“我的拼团”。",
                    )
                )

        if "order_query" in capabilities:
            if order_id is not None:
                results.append(self.gateway_client.get_order(order_id, authorization))
            else:
                results.append(self.gateway_client.list_orders(authorization))

        if (
            "order_preview" in capabilities
            and package_result
            and package_result.has_data
        ):
            preview_data: dict[str, object] = {
                "package": package_result.data,
                "quantity": quantity,
                "previewOnly": True,
            }
            price = (
                package_result.data.get("price")
                if isinstance(package_result.data, dict)
                else None
            )
            if isinstance(price, int):
                preview_data["estimatedTotalAmountBeforeDiscount"] = price * quantity
                preview_data["amountUnit"] = "cents"
            if coupon_result and coupon_result.has_data:
                preview_data["candidateCoupons"] = coupon_result.data
            results.append(
                GatewayQueryResult(
                    capability="order_preview",
                    status="success",
                    endpoint="composed-read-model",
                    data=preview_data,
                    reference_id=f"order-preview:package:{package_id}",
                    reference_title=f"套餐 {package_id} 只读订单预览",
                )
            )

        return results


def _fallback_rewrite(message: str) -> TradeQueryRewrite:
    capability_keywords: list[tuple[TradeCapability, tuple[str, ...]]] = [
        ("price_query", ("价格", "多少钱", "售价", "费用")),
        ("stock_query", ("库存", "有货", "余量", "剩余")),
        ("coupon_query", ("优惠券", "代金券", "折扣券", "券")),
        ("group_buy_query", ("拼团", "团购", "成团", "拼单")),
        ("order_preview", ("订单预览", "预览订单", "结算预览", "预估金额")),
        ("order_query", ("查询订单", "我的订单", "订单详情", "订单进度", "订单状态")),
    ]
    normalized = message.lower()
    capabilities = [
        capability
        for capability, keywords in capability_keywords
        if any(keyword.lower() in normalized for keyword in keywords)
    ]
    if "预览" in message and any(subject in message for subject in ("订单", "套餐")):
        capabilities.append("order_preview")
        capabilities = list(dict.fromkeys(capabilities))
    if not capabilities:
        capabilities.append("order_query")
    return TradeQueryRewrite(
        search_query=message,
        capabilities=capabilities,
        reason="fallback rewrite kept the original user query",
    )


def _extract_int(message: str, patterns: tuple[re.Pattern[str], ...]) -> int | None:
    text = _extract_text(message, patterns)
    return int(text) if text else None


def _extract_text(message: str, patterns: tuple[re.Pattern[str], ...]) -> str | None:
    for pattern in patterns:
        match = pattern.search(message)
        if match:
            return match.group(1)
    return None


def _extract_quantity(message: str) -> int:
    quantity = _extract_int(message, _QUANTITY_PATTERNS)
    if quantity is None:
        return 1
    return min(max(quantity, 1), 99)


def _asks_for_my_coupons(message: str) -> bool:
    return any(
        keyword in message
        for keyword in ("我的优惠券", "我有的券", "已领取", "可用优惠券")
    )


def _asks_for_my_group_buys(message: str) -> bool:
    return any(
        keyword in message for keyword in ("我的拼团", "拼团订单", "我参加的拼团")
    )


def _missing_parameter(capability: str, message: str) -> GatewayQueryResult:
    return GatewayQueryResult(
        capability=capability,
        status="missing_parameter",
        message=message,
    )


def _facts(results: list[GatewayQueryResult]) -> list[dict[str, object]]:
    return [
        {
            "id": result.reference_id,
            "title": result.reference_title,
            "source": "java_gateway_api",
            "endpoint": result.endpoint,
            "data": result.data,
        }
        for result in results
    ]


def _references(results: list[GatewayQueryResult]) -> list[Reference]:
    seen: set[str] = set()
    references: list[Reference] = []
    for result in results:
        if not result.reference_id or result.reference_id in seen:
            continue
        seen.add(result.reference_id)
        references.append(
            Reference(
                id=result.reference_id,
                title=result.reference_title,
                source="java_gateway_api",
                score=1.0,
            )
        )
    return references


def _context_summary(facts: list[dict[str, object]]) -> str:
    return "\n".join(
        f"{index}. {fact['title']}: "
        f"{json.dumps(fact['data'], ensure_ascii=False, separators=(',', ':'), default=str)}"
        for index, fact in enumerate(facts, start=1)
    )


def _template_answer(context: str) -> str:
    return f"已通过 Java Gateway 查询到以下实时业务事实（金额单位：分）：\n{context}"


def _no_facts_message(results: list[GatewayQueryResult]) -> str:
    messages = list(
        dict.fromkeys(result.message for result in results if result.message)
    )
    if not messages:
        return "当前未取得可用于回答的实时业务事实，请补充查询条件后重试。"
    return "当前未取得可用于回答的实时业务事实：" + "；".join(messages)


def _rewrite_messages(message: str) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You classify read-only trade queries for a food-life business system. "
                "Return only valid JSON with search_query, capabilities, and reason. "
                "Capabilities can include price_query, stock_query, coupon_query, "
                "group_buy_query, order_query, order_preview. "
                "Do not invent IDs or add transaction actions. Preserve identifiers and filters "
                "from the original question in search_query."
            ),
        ),
        ("human", message),
    ]


def _answer_messages(
    message: str,
    rewrite: TradeQueryRewrite,
    context: str,
) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You are TradeAgent for a food-life business system. "
                "Answer only from the supplied current facts returned by Java Gateway APIs. "
                "You may explain price, stock, coupons, group buys, orders, and read-only previews. "
                "Never claim that an order was created, cancelled, paid, refunded, or otherwise changed. "
                "Amounts are in cents. A composed order preview is preliminary: do not claim a final "
                "payable amount or coupon eligibility unless the supplied facts prove it. "
                "Return only valid JSON with answer and used_reference_ids."
            ),
        ),
        (
            "human",
            (
                f"User question: {message}\n"
                f"Rewritten query: {rewrite.search_query}\n"
                f"Detected capabilities: {', '.join(rewrite.capabilities)}\n"
                f"Java Gateway current facts:\n{context}"
            ),
        ),
    ]

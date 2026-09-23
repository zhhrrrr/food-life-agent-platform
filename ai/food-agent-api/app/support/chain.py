import logging
import re
from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model
from app.rag.context_builder import build_context_items, build_context_summary, build_references
from app.rag.retriever import RagRetriever
from app.search.schemas import Reference, SearchHit
from app.support.order_status_client import JavaOrderStatusClient, OrderStatusLookup

logger = logging.getLogger("food_agent.support.chain")

SupportCapability = Literal[
    "refund_rule",
    "group_buy_rule",
    "payment_rule",
    "order_status_explanation",
    "platform_faq",
]

_ORDER_ID_PATTERNS = (
    re.compile(r"(?:\u8ba2\u5355(?:\u53f7|ID)?|order(?:\s*id|\s*no)?)\s*[:\uff1a#]?\s*(\d{1,19})", re.IGNORECASE),
    re.compile(r"#(\d{1,19})"),
)


class SupportAnswer(BaseModel):
    answer: str
    used_reference_ids: list[str] = Field(default_factory=list)


class SupportChainResult(BaseModel):
    response: str
    rag_mode: str
    query: str
    capabilities: list[str] = Field(default_factory=list)
    context: list[dict[str, object]] = Field(default_factory=list)
    references: list[dict[str, object]] = Field(default_factory=list)
    order_status: dict[str, object] | None = None


class SupportChain:
    def __init__(
        self,
        settings: Settings | None = None,
        retriever: RagRetriever | None = None,
        order_status_client: JavaOrderStatusClient | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.retriever = retriever or RagRetriever(settings=self.settings)
        self.order_status_client = order_status_client or JavaOrderStatusClient(settings=self.settings)

    def run(self, message: str, authorization: str = "") -> SupportChainResult:
        capabilities = _detect_capabilities(message)
        retrieval = self.retriever.retrieve("support", message, size=5)
        order_lookup = self._lookup_order_status(message, capabilities, authorization)

        if not retrieval.has_hits and not order_lookup.has_snapshot:
            return self._no_evidence_result(message, capabilities, order_lookup)

        rule_context = build_context_summary(retrieval.hits)
        order_context = _order_context(order_lookup)
        context = _join_context(rule_context, order_context)
        references = _references(retrieval.hits, order_lookup)
        answer = self.generate_answer(message, capabilities, context, references, order_lookup)
        return SupportChainResult(
            response=answer.answer,
            rag_mode="support_agent_llm_rag",
            query=message,
            capabilities=capabilities,
            context=build_context_items(retrieval.hits),
            references=[reference.model_dump() for reference in references],
            order_status=order_lookup.snapshot.model_dump() if order_lookup.snapshot else None,
        )

    def _lookup_order_status(
        self,
        message: str,
        capabilities: list[SupportCapability],
        authorization: str,
    ) -> OrderStatusLookup:
        if "order_status_explanation" not in capabilities:
            return OrderStatusLookup(status="not_requested")

        order_id = _extract_order_id(message)
        if order_id is None:
            return OrderStatusLookup(status="missing_order_id")
        return self.order_status_client.get_order_status(order_id, authorization)

    def generate_answer(
        self,
        message: str,
        capabilities: list[SupportCapability],
        context: str,
        references: list[Reference],
        order_lookup: OrderStatusLookup,
    ) -> SupportAnswer:
        fallback = SupportAnswer(
            answer=_template_answer(context, order_lookup),
            used_reference_ids=[reference.id for reference in references],
        )
        if not self.settings.llm_router_enabled:
            return fallback

        try:
            model = build_chat_model(self.settings).with_structured_output(
                SupportAnswer,
                **_structured_output_kwargs(self.settings),
            )
            result = model.invoke(_answer_messages(message, capabilities, context, order_lookup))
        except ChatModelUnavailableError as exc:
            logger.info("support_answer_fallback reason=%s", exc)
            return fallback
        except Exception:
            logger.exception("support_answer_failed")
            return fallback

        try:
            return result if isinstance(result, SupportAnswer) else SupportAnswer.model_validate(result)
        except Exception:
            logger.exception("support_answer_invalid")
            return fallback

    def _no_evidence_result(
        self,
        message: str,
        capabilities: list[SupportCapability],
        order_lookup: OrderStatusLookup,
    ) -> SupportChainResult:
        return SupportChainResult(
            response=_no_evidence_message(order_lookup),
            rag_mode="support_agent_llm_rag",
            query=message,
            capabilities=capabilities,
        )


def _detect_capabilities(message: str) -> list[SupportCapability]:
    capability_keywords: list[tuple[SupportCapability, tuple[str, ...]]] = [
        ("refund_rule", ("\u9000\u6b3e", "\u9000\u6b3e", "\u552e\u540e", "\u9000\u6b3e")),
        ("group_buy_rule", ("\u62fc\u56e2", "\u62fc\u5355", "\u7ec4\u56e2", "\u6210\u56e2")),
        ("payment_rule", ("\u652f\u4ed8", "\u4ed8\u6b3e", "\u6263\u6b3e", "\u652f\u4ed8\u5931\u8d25")),
        (
            "order_status_explanation",
            ("\u8ba2\u5355\u72b6\u6001", "\u8ba2\u5355\u8fdb\u5ea6", "\u8ba2\u5355\u600e\u4e48\u6837", "order status"),
        ),
    ]
    capabilities = [
        capability
        for capability, keywords in capability_keywords
        if any(keyword.lower() in message.lower() for keyword in keywords)
    ]
    return capabilities or ["platform_faq"]


def _extract_order_id(message: str) -> int | None:
    for pattern in _ORDER_ID_PATTERNS:
        match = pattern.search(message)
        if match:
            return int(match.group(1))
    return None


def _references(hits: list[SearchHit], order_lookup: OrderStatusLookup) -> list[Reference]:
    references = build_references(hits)
    if order_lookup.snapshot:
        references.append(
            Reference(
                id=f"order:{order_lookup.snapshot.order_id}",
                title=f"Order {order_lookup.snapshot.order_id} status",
                source="java_trade_order_query",
                score=1.0,
            )
        )
    return references


def _order_context(order_lookup: OrderStatusLookup) -> str:
    snapshot = order_lookup.snapshot
    if snapshot is None:
        return ""
    return (
        "Java order status snapshot (current state): "
        f"order_id={snapshot.order_id}, order_no={snapshot.order_no}, "
        f"trade_type={snapshot.trade_type}, order_status={snapshot.order_status}"
    )


def _join_context(rule_context: str, order_context: str) -> str:
    return "\n".join(part for part in (rule_context, order_context) if part)


def _template_answer(context: str, order_lookup: OrderStatusLookup) -> str:
    suffix = _lookup_note(order_lookup)
    return f"\u6839\u636e\u5e73\u53f0\u89c4\u5219\u548c\u5f53\u524d\u67e5\u8be2\u7ed3\u679c\uff0c\u53ef\u4ee5\u53c2\u8003\uff1a\n{context}{suffix}"


def _no_evidence_message(order_lookup: OrderStatusLookup) -> str:
    note = _lookup_note(order_lookup)
    if note:
        return note.lstrip("\n")
    return (
        "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u76f8\u5173\u5e73\u53f0\u89c4\u5219\u6216 FAQ \u4f9d\u636e\uff0c"
        "\u8bf7\u5148\u5c06\u6b63\u5f0f\u8fd0\u8425 Markdown \u6216\u914d\u7f6e\u6587\u6863\u5bfc\u5165\u89c4\u5219\u7d22\u5f15\u3002"
    )


def _lookup_note(order_lookup: OrderStatusLookup) -> str:
    notes = {
        "missing_order_id": "\n\u5982\u9700\u67e5\u8be2\u8ba2\u5355\u5f53\u524d\u72b6\u6001\uff0c\u8bf7\u63d0\u4f9b\u8ba2\u5355 ID\u3002",
        "missing_authorization": "\n\u67e5\u8be2\u8ba2\u5355\u5f53\u524d\u72b6\u6001\u9700\u8981\u643a\u5e26\u5df2\u767b\u5f55\u7528\u6237\u7684 Authorization\u3002",
        "not_found": "\n\u672a\u67e5\u5230\u8be5\u7528\u6237\u7684\u8ba2\u5355\uff0c\u8bf7\u6838\u5bf9\u8ba2\u5355 ID\u3002",
        "unavailable": "\n\u8ba2\u5355\u72b6\u6001\u67e5\u8be2\u6682\u4e0d\u53ef\u7528\uff0c\u89c4\u5219\u8bf4\u660e\u4ecd\u4ee5\u5f53\u524d\u89c4\u5219\u5e93\u4e3a\u51c6\u3002",
    }
    return notes.get(order_lookup.status, "")


def _structured_output_kwargs(settings: Settings) -> dict[str, str]:
    if settings.llm_provider.strip().lower() == "zhipu":
        return {"method": "json_mode"}
    return {}


def _answer_messages(
    message: str,
    capabilities: list[SupportCapability],
    context: str,
    order_lookup: OrderStatusLookup,
) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You are SupportAgent for a food-life business system. "
                "Answer only from the supplied platform-rule context and Java order-status snapshot. "
                "Explain refund, group-buy, payment, order-status, and FAQ questions clearly. "
                "The Java snapshot is current state, while ES documents are rule evidence. "
                "Do not create, cancel, pay, refund, or otherwise modify orders. "
                "Do not claim an order status unless a Java snapshot is present. "
                "Return only valid JSON with answer and used_reference_ids."
            ),
        ),
        (
            "human",
            (
                f"User question: {message}\n"
                f"Detected capabilities: {', '.join(capabilities)}\n"
                f"Order lookup status: {order_lookup.status}\n"
                f"Evidence context:\n{context}"
            ),
        ),
    ]

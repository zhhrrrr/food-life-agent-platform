import logging
from typing import Any, Literal

from pydantic import Field

from app.core.config import Settings, get_settings
from app.llm.structured_output import invoke_structured_output_with_fallback
from app.rag.chain_models import (
    AgentAnswer,
    AgentChainResult,
    QueryRewriteBase,
    merge_capabilities,
    select_references,
)
from app.rag.context_builder import (
    build_context_items,
    build_context_summary,
    build_references,
)
from app.rag.retriever import RagRetriever
from app.search.schemas import Reference

logger = logging.getLogger("food_agent.support.chain")

SupportCapability = Literal[
    "refund_rule",
    "group_buy_rule",
    "payment_rule",
    "order_status_explanation",
    "platform_faq",
]


class SupportQueryRewrite(QueryRewriteBase):
    capabilities: list[SupportCapability] = Field(min_length=1)


class SupportChain:
    def __init__(
        self,
        settings: Settings | None = None,
        retriever: RagRetriever | None = None,
        chat_model: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.retriever = retriever or RagRetriever(settings=self.settings)
        self.chat_model = chat_model

    def run(self, message: str) -> AgentChainResult:
        rewrite = self.rewrite_query(message)
        retrieval = self.retriever.retrieve("support", rewrite.search_query, size=5)

        if not retrieval.has_hits:
            return AgentChainResult(
                response=_no_evidence_message(),
                rag_mode="support_agent_llm_rag",
                query=message,
                rewritten_query=rewrite.search_query,
                capabilities=list(rewrite.capabilities),
            )

        context = build_context_summary(retrieval.hits)
        references = build_references(retrieval.hits)
        answer = self.generate_answer(message, rewrite, context, references)
        reference_dicts = [reference.model_dump() for reference in references]
        return AgentChainResult(
            response=answer.answer,
            rag_mode="support_agent_llm_rag",
            query=message,
            rewritten_query=rewrite.search_query,
            capabilities=list(rewrite.capabilities),
            context=build_context_items(retrieval.hits),
            references=select_references(
                reference_dicts,
                answer.used_reference_ids,
            ),
        )

    def rewrite_query(self, message: str) -> SupportQueryRewrite:
        fallback = _fallback_rewrite(message)
        result = invoke_structured_output_with_fallback(
            settings=self.settings,
            schema=SupportQueryRewrite,
            messages=_rewrite_messages(message),
            fallback=fallback,
            operation="support_query_rewrite",
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

    def generate_answer(
        self,
        message: str,
        rewrite: SupportQueryRewrite,
        context: str,
        references: list[Reference],
    ) -> AgentAnswer:
        fallback = AgentAnswer(
            answer=_template_answer(context),
            used_reference_ids=[reference.id for reference in references],
        )
        return invoke_structured_output_with_fallback(
            settings=self.settings,
            schema=AgentAnswer,
            messages=_answer_messages(message, rewrite, context),
            fallback=fallback,
            operation="support_answer",
            logger=logger,
            chat_model=self.chat_model,
        )


def _fallback_rewrite(message: str) -> SupportQueryRewrite:
    capability_keywords: list[tuple[SupportCapability, tuple[str, ...]]] = [
        ("refund_rule", ("退款", "退钱", "售后", "退费")),
        ("group_buy_rule", ("拼团", "拼单", "组团", "成团")),
        ("payment_rule", ("支付", "付款", "扣款", "支付失败")),
        (
            "order_status_explanation",
            ("订单状态", "状态含义", "状态说明", "order status"),
        ),
    ]
    normalized = message.lower()
    capabilities = [
        capability
        for capability, keywords in capability_keywords
        if any(keyword.lower() in normalized for keyword in keywords)
    ]
    return SupportQueryRewrite(
        search_query=message,
        capabilities=capabilities or ["platform_faq"],
        reason="fallback rewrite kept the original user query",
    )


def _template_answer(context: str) -> str:
    return f"根据平台规则，可以参考：\n{context}"


def _no_evidence_message() -> str:
    return (
        "暂未从 ES 检索到相关平台规则或 FAQ 依据，"
        "请先将正式运营 Markdown 或配置文档导入规则索引。"
    )


def _answer_messages(
    message: str,
    rewrite: SupportQueryRewrite,
    context: str,
) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You are SupportAgent for a food-life business system. "
                "Answer only from the supplied Elasticsearch platform-rule context. "
                "Explain refund, group-buy, payment, generic order-status meanings, and FAQ clearly. "
                "Do not claim the current state of a concrete order; that belongs to TradeAgent. "
                "Do not create, cancel, pay, refund, or otherwise modify orders. "
                "Return only valid JSON with answer and used_reference_ids."
            ),
        ),
        (
            "human",
            (
                f"User question: {message}\n"
                f"Rewritten query: {rewrite.search_query}\n"
                f"Detected capabilities: {', '.join(rewrite.capabilities)}\n"
                f"Platform-rule context:\n{context}"
            ),
        ),
    ]


def _rewrite_messages(message: str) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You rewrite food-platform support questions for Elasticsearch BM25 rule retrieval. "
                "Return only valid JSON with search_query, capabilities, and reason. "
                "Capabilities can include refund_rule, group_buy_rule, payment_rule, "
                "order_status_explanation, platform_faq. "
                "Keep rule topics, status meanings, payment conditions, refund conditions, "
                "and group-buy conditions in search_query. Do not invent or query an order ID."
            ),
        ),
        ("human", message),
    ]

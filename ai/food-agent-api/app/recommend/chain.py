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
from app.search.schemas import SearchHit

logger = logging.getLogger("food_agent.recommend.chain")

RecommendCapability = Literal[
    "shop_recommendation",
    "package_recommendation",
    "cuisine_filter",
    "budget_filter",
    "scene_recommendation",
    "review_semantic_search",
    "shop_comparison",
]


class RecommendQueryRewrite(QueryRewriteBase):
    capabilities: list[RecommendCapability] = Field(min_length=1)


class RecommendChain:
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
        retrieval = self.retriever.retrieve("recommend", rewrite.search_query, size=5)

        if not retrieval.has_hits:
            return AgentChainResult(
                response=(
                    "暂未从 ES 检索到可用于推荐的商家、套餐、评价、活动或优惠券依据，"
                    "建议先同步相关业务文档后再进行推荐。"
                ),
                rag_mode="recommend_agent_llm_rag",
                query=message,
                rewritten_query=rewrite.search_query,
                capabilities=list(rewrite.capabilities),
            )

        context = build_context_summary(retrieval.hits)
        references = build_references(retrieval.hits)
        answer = self.generate_answer(message, rewrite, retrieval.hits, context)
        reference_dicts = [reference.model_dump() for reference in references]

        return AgentChainResult(
            response=answer.answer,
            rag_mode="recommend_agent_llm_rag",
            query=message,
            rewritten_query=rewrite.search_query,
            capabilities=list(rewrite.capabilities),
            context=build_context_items(retrieval.hits),
            references=select_references(reference_dicts, answer.used_reference_ids),
        )

    def rewrite_query(self, message: str) -> RecommendQueryRewrite:
        fallback = _fallback_rewrite(message)
        result = invoke_structured_output_with_fallback(
            settings=self.settings,
            schema=RecommendQueryRewrite,
            messages=_rewrite_messages(message),
            fallback=fallback,
            operation="recommend_query_rewrite",
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
        rewrite: RecommendQueryRewrite,
        hits: list[SearchHit],
        context: str,
    ) -> AgentAnswer:
        fallback = AgentAnswer(
            answer=_template_answer(context),
            used_reference_ids=[hit.id for hit in hits],
        )
        return invoke_structured_output_with_fallback(
            settings=self.settings,
            schema=AgentAnswer,
            messages=_answer_messages(message, rewrite, context),
            fallback=fallback,
            operation="recommend_answer",
            logger=logger,
            chat_model=self.chat_model,
        )


def _fallback_rewrite(message: str) -> RecommendQueryRewrite:
    capabilities: list[RecommendCapability] = []
    keyword_map: list[tuple[RecommendCapability, tuple[str, ...]]] = [
        ("shop_recommendation", ("商家", "餐厅", "饭店", "店", "推荐")),
        ("package_recommendation", ("套餐", "双人餐", "团购")),
        ("cuisine_filter", ("川菜", "火锅", "烧烤", "粤菜", "日料", "西餐")),
        ("budget_filter", ("预算", "便宜", "人均", "价格", "元以内")),
        ("scene_recommendation", ("约会", "聚餐", "家庭", "商务", "朋友", "两个人")),
        ("review_semantic_search", ("评价", "口碑", "好吃", "环境", "服务")),
        ("shop_comparison", ("比较", "对比", "哪个更好", "哪家更好")),
    ]
    for capability, keywords in keyword_map:
        if any(keyword in message for keyword in keywords):
            capabilities.append(capability)

    if not capabilities:
        capabilities.append("shop_recommendation")

    return RecommendQueryRewrite(
        search_query=message,
        capabilities=capabilities,
        reason="fallback rewrite kept the original user query",
    )


def _template_answer(context: str) -> str:
    return f"根据检索结果，为你整理出以下推荐线索：\n{context}"


def _rewrite_messages(message: str) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You rewrite food recommendation requests for Elasticsearch BM25 retrieval. "
                "Return only valid JSON with search_query, capabilities, and reason. "
                "Capabilities can include shop_recommendation, package_recommendation, "
                "cuisine_filter, budget_filter, scene_recommendation, "
                "review_semantic_search, shop_comparison. "
                "Keep business constraints such as cuisine, budget, scene, package, "
                "reviews, coupons, activities, and comparison targets in search_query."
            ),
        ),
        ("human", message),
    ]


def _answer_messages(
    message: str,
    rewrite: RecommendQueryRewrite,
    context: str,
) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "You are RecommendAgent for a food-life business system. "
                "Answer only from the provided ES/RAG context. "
                "You can recommend shops, packages, cuisine matches, budget-friendly choices, "
                "scene-based options, review-backed options, and compare shops. "
                "Treat prices, activities, and coupons as discovery evidence. Do not claim current "
                "stock, remaining supply, availability, or user eligibility from ES. "
                "Do not create orders, lock stock, or claim unsupported facts. "
                "Return only valid JSON with answer and used_reference_ids. "
                "The answer should be concise, practical, and include why each option fits."
            ),
        ),
        (
            "human",
            (
                f"User question: {message}\n"
                f"Rewritten query: {rewrite.search_query}\n"
                f"Detected capabilities: {', '.join(rewrite.capabilities)}\n"
                f"ES/RAG context:\n{context}"
            ),
        ),
    ]

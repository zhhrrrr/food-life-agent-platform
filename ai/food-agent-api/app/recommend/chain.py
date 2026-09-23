import logging
from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model
from app.rag.context_builder import build_context_items, build_context_summary, build_references
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


class RecommendQueryRewrite(BaseModel):
    search_query: str
    capabilities: list[RecommendCapability] = Field(default_factory=list)
    reason: str = ""


class RecommendAnswer(BaseModel):
    answer: str
    used_reference_ids: list[str] = Field(default_factory=list)


class RecommendChainResult(BaseModel):
    response: str
    rag_mode: str
    query: str
    rewritten_query: str
    capabilities: list[str] = Field(default_factory=list)
    context: list[dict[str, object]] = Field(default_factory=list)
    references: list[dict[str, object]] = Field(default_factory=list)


class RecommendChain:
    def __init__(
        self,
        settings: Settings | None = None,
        retriever: RagRetriever | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.retriever = retriever or RagRetriever(settings=self.settings)

    def run(self, message: str) -> RecommendChainResult:
        rewrite = self.rewrite_query(message)
        retrieval = self.retriever.retrieve("recommend", rewrite.search_query, size=5)

        if not retrieval.has_hits:
            return RecommendChainResult(
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

        return RecommendChainResult(
            response=answer.answer,
            rag_mode="recommend_agent_llm_rag",
            query=message,
            rewritten_query=rewrite.search_query,
            capabilities=list(rewrite.capabilities),
            context=build_context_items(retrieval.hits),
            references=[reference.model_dump() for reference in references],
        )

    def rewrite_query(self, message: str) -> RecommendQueryRewrite:
        fallback = _fallback_rewrite(message)
        if not self.settings.llm_router_enabled:
            return fallback

        try:
            model = build_chat_model(self.settings).with_structured_output(
                RecommendQueryRewrite,
                **_structured_output_kwargs(self.settings),
            )
            result = model.invoke(_rewrite_messages(message))
        except ChatModelUnavailableError as exc:
            logger.info("recommend_query_rewrite_fallback reason=%s", exc)
            return fallback
        except Exception:
            logger.exception("recommend_query_rewrite_failed")
            return fallback

        try:
            return result if isinstance(result, RecommendQueryRewrite) else RecommendQueryRewrite.model_validate(result)
        except Exception:
            logger.exception("recommend_query_rewrite_invalid")
            return fallback

    def generate_answer(
        self,
        message: str,
        rewrite: RecommendQueryRewrite,
        hits: list[SearchHit],
        context: str,
    ) -> RecommendAnswer:
        fallback = RecommendAnswer(answer=_template_answer(context), used_reference_ids=[hit.id for hit in hits])
        if not self.settings.llm_router_enabled:
            return fallback

        try:
            model = build_chat_model(self.settings).with_structured_output(
                RecommendAnswer,
                **_structured_output_kwargs(self.settings),
            )
            result = model.invoke(_answer_messages(message, rewrite, context))
        except ChatModelUnavailableError as exc:
            logger.info("recommend_answer_fallback reason=%s", exc)
            return fallback
        except Exception:
            logger.exception("recommend_answer_failed")
            return fallback

        try:
            return result if isinstance(result, RecommendAnswer) else RecommendAnswer.model_validate(result)
        except Exception:
            logger.exception("recommend_answer_invalid")
            return fallback


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


def _structured_output_kwargs(settings: Settings) -> dict[str, str]:
    if settings.llm_provider.strip().lower() == "zhipu":
        return {"method": "json_mode"}
    return {}


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

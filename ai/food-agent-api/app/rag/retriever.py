import logging
from time import monotonic

from langsmith import traceable
from app.core.langsmith import redact, trace_inputs

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.rag.embedding import RagEmbeddingService
from app.rag.fusion import reciprocal_rank_fusion
from app.rag.reranker import RagReranker
from app.search.index_names import (
    index_names_for_scopes,
    recommend_scopes_for_capabilities,
)
from app.search.schemas import SearchFilters, SearchHit
from app.search.search_service import SearchService
from app.vector.collection_names import collection_aliases_for_scopes
from app.vector.milvus_service import MilvusVectorService

logger = logging.getLogger("food_agent.rag.retriever")


class RetrievalResult(BaseModel):
    agent: str
    query: str
    hits: list[SearchHit] = Field(default_factory=list)
    mode: str = "bm25"
    candidate_counts: dict[str, int] = Field(default_factory=dict)

    @property
    def has_hits(self) -> bool:
        return bool(self.hits)


class RagRetriever:
    def __init__(
        self,
        settings: Settings | None = None,
        search_service: SearchService | None = None,
        embedding_service: RagEmbeddingService | None = None,
        vector_service: MilvusVectorService | None = None,
        reranker: RagReranker | None = None,
        *, strict: bool = False,
    ) -> None:
        self.settings = settings or get_settings()
        self.strict = strict
        self.search_service = search_service or SearchService(self.settings, strict=strict)
        self.embedding_service = embedding_service
        self.vector_service = vector_service
        if self.settings.rag_hybrid_enabled and self.settings.milvus_enabled:
            self.vector_service = self.vector_service or MilvusVectorService(self.settings)
        if self.vector_service is not None and self.embedding_service is None:
            self.embedding_service = RagEmbeddingService(self.settings)
        self.reranker = reranker or RagReranker(self.settings, strict=strict)

    @traceable(name="rag.retrieve", run_type="retriever", process_inputs=trace_inputs, process_outputs=redact)
    def retrieve(
        self,
        agent: str,
        query: str,
        size: int = 3,
        *,
        original_query: str | None = None,
        rerank_query: str | None = None,
        capabilities: list[str] | None = None,
        filters: SearchFilters | None = None,
    ) -> RetrievalResult:
        started = monotonic()
        scopes = self._scopes_for_agent(agent, capabilities)
        if self.strict and self.settings.rag_hybrid_enabled and not self.settings.milvus_enabled:
            raise RuntimeError("Hybrid evaluation requires Milvus")
        if not scopes:
            if self.strict:
                raise ValueError("Evaluation requires supported retrieval scopes")
            return RetrievalResult(agent=agent, query=query)
        indices = index_names_for_scopes(self.settings, scopes)

        candidate_size = max(size, self.settings.rag_candidate_size)
        lexical_hits = self.search_service.search_many(
            indices,
            query,
            size=candidate_size,
            original_query=original_query,
            filters=filters,
        )
        vector_hits: list[SearchHit] = []
        if self.vector_service is not None and self.embedding_service is not None:
            try:
                query_vector = self.embedding_service.embed_query(
                    _embedding_query(query, original_query)
                )
                recall_filters = filters
                if agent == "recommend" and filters is not None and (
                    filters.min_avg_price_cents is not None or filters.max_avg_price_cents is not None
                ):
                    shops = self.vector_service.search_many(
                        collection_aliases_for_scopes(self.settings, ["shops"]), query_vector,
                        size=candidate_size, filters=filters)
                    shop_ids = list(dict.fromkeys(
                        hit.metadata["shopId"] for hit in shops
                        if type(hit.metadata.get("shopId")) is int and hit.metadata["shopId"] > 0
                        and hit.id == f'shop-{hit.metadata["shopId"]}' and _matches_avg_price(hit, filters)))
                    recall_filters = filters.model_copy(update={"shop_ids": shop_ids}) if shop_ids else None
                    if recall_filters is not None:
                        lexical_hits = self.search_service.search_many(
                            indices, query, size=candidate_size, original_query=original_query,
                            filters=recall_filters)
                if recall_filters is not None or filters is None:
                    vector_hits = self.vector_service.search_many(
                        collection_aliases_for_scopes(self.settings, scopes), query_vector,
                        size=candidate_size, filters=recall_filters)
            except Exception as exc:  # noqa: BLE001 - isolate the external provider.
                if self.strict:
                    raise
                logger.warning(
                    "rag_vector_retrieval_failed agent=%s error=%s",
                    agent,
                    type(exc).__name__,
                )
        if lexical_hits and vector_hits:
            fused_hits = reciprocal_rank_fusion(
                [("bm25", lexical_hits), ("vector", vector_hits)],
                rank_constant=self.settings.rag_rrf_k,
            )
        else:
            fused_hits = lexical_hits or vector_hits
        rerank_result = self.reranker.rerank(
            rerank_query or original_query or query,
            fused_hits,
            size=candidate_size,
        )
        reranked_hits = rerank_result.hits
        if agent == "recommend" and filters is not None:
            reranked_hits = self._with_shop_price_evidence(reranked_hits, filters)
        hits = (
            self._diversify(
                reranked_hits, size=size, max_per_source=2,
                require_shop_price=filters is not None and (
                    filters.min_avg_price_cents is not None or filters.max_avg_price_cents is not None))
            if agent == "recommend"
            else reranked_hits[:size]
        )
        mode = _retrieval_mode(
            has_lexical=bool(lexical_hits),
            has_vector=bool(vector_hits),
            rerank_applied=rerank_result.applied,
        )
        logger.info(
            "rag_retrieval_completed agent=%s mode=%s indices=%s bm25=%s vector=%s "
            "fused=%s hits=%s latency_ms=%.2f",
            agent,
            mode,
            ",".join(indices),
            len(lexical_hits),
            len(vector_hits),
            len(fused_hits),
            len(hits),
            (monotonic() - started) * 1000,
        )
        return RetrievalResult(
            agent=agent,
            query=query,
            hits=hits,
            mode=mode,
            candidate_counts={
                "bm25": len(lexical_hits),
                "vector": len(vector_hits),
                "fused": len(fused_hits),
            },
        )

    def _with_shop_price_evidence(
        self, hits: list[SearchHit], filters: SearchFilters,
    ) -> list[SearchHit]:
        minimum, maximum = filters.min_avg_price_cents, filters.max_avg_price_cents
        if minimum is None and maximum is None:
            return hits

        # A package total or an unpriced review cannot prove a per-person budget.
        # Link the actual shop document, preserving its source as separate evidence.
        shops: dict[str, SearchHit | None] = {}
        selected: list[SearchHit] = []
        for hit in hits:
            if hit.id.startswith("shop-"):
                if _matches_avg_price(hit, filters):
                    selected.append(hit)
                continue
            shop_id = hit.metadata.get("shopId")
            if type(shop_id) is not int or shop_id <= 0:
                continue
            key = f"shop-{shop_id}"
            if key not in shops:
                document = self.search_service.get_document(self.settings.es_index_shop, key)
                shops[key] = (SearchHit(**document.model_dump(), score=hit.score,
                                       retrieval_scores={"related": hit.score})
                              if document is not None and document.id == key
                              and document.metadata.get("shopId") == shop_id else None)
            shop = shops[key]
            if shop is not None and _matches_avg_price(shop, filters):
                selected.extend([shop, hit])
        return selected

    @staticmethod
    def _scopes_for_agent(
        agent: str,
        capabilities: list[str] | None,
    ) -> list[str]:
        if agent == "recommend":
            return recommend_scopes_for_capabilities(capabilities)
        if agent == "support":
            return ["rules"]
        return []

    @staticmethod
    def _diversify(
        candidates: list[SearchHit],
        *,
        size: int,
        max_per_source: int,
        require_shop_price: bool = False,
    ) -> list[SearchHit]:
        selected: list[SearchHit] = []
        source_counts: dict[str, int] = {}
        seen_ids: set[str] = set()
        for hit in candidates:
            if hit.id in seen_ids:
                continue
            if (require_shop_price and not hit.id.startswith("shop-")
                    and f'shop-{hit.metadata.get("shopId")}' not in seen_ids):
                continue
            count = source_counts.get(hit.source, 0)
            if count >= max_per_source:
                continue
            selected.append(hit)
            seen_ids.add(hit.id)
            source_counts[hit.source] = count + 1
            if len(selected) >= size:
                break
        return selected


def _matches_avg_price(hit: SearchHit, filters: SearchFilters) -> bool:
    price = hit.metadata.get("avgPrice")
    return (type(price) in (int, float) and price >= 0
            and (filters.min_avg_price_cents is None or price >= filters.min_avg_price_cents)
            and (filters.max_avg_price_cents is None or price <= filters.max_avg_price_cents))


def _embedding_query(query: str, original_query: str | None) -> str:
    if not original_query or original_query.strip() == query.strip():
        return query
    return f"{query}\n{original_query}"


def _retrieval_mode(
    *,
    has_lexical: bool,
    has_vector: bool,
    rerank_applied: bool,
) -> str:
    if has_lexical and has_vector:
        base = "hybrid_rrf"
    elif has_vector:
        base = "vector"
    else:
        base = "bm25"
    return f"{base}_rerank" if rerank_applied else base

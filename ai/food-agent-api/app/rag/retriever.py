import logging
from time import monotonic

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.rag.embedding import RagEmbeddingService
from app.rag.fusion import reciprocal_rank_fusion
from app.rag.reranker import RagReranker
from app.search.index_names import recommend_indices_for_capabilities, support_indices
from app.search.schemas import SearchFilters, SearchHit
from app.search.search_service import SearchService

logger = logging.getLogger("food_agent.rag.retriever")


class RetrievalResult(BaseModel):
    agent: str
    query: str
    indices: list[str] = Field(default_factory=list)
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
        reranker: RagReranker | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.search_service = search_service or SearchService(self.settings)
        self.embedding_service = embedding_service or getattr(
            self.search_service,
            "embedding_service",
            None,
        )
        if self.settings.rag_hybrid_enabled and self.embedding_service is None:
            self.embedding_service = RagEmbeddingService(self.settings)
        self.reranker = reranker or RagReranker(self.settings)

    def retrieve(
        self,
        agent: str,
        query: str,
        size: int = 3,
        *,
        original_query: str | None = None,
        capabilities: list[str] | None = None,
        filters: SearchFilters | None = None,
    ) -> RetrievalResult:
        started = monotonic()
        indices = self._indices_for_agent(agent, capabilities)
        if not indices:
            return RetrievalResult(agent=agent, query=query)

        candidate_size = max(size, self.settings.rag_candidate_size)
        lexical_hits = self.search_service.search_many(
            indices,
            query,
            size=candidate_size,
            original_query=original_query,
            filters=filters,
        )
        rankings = [("bm25", lexical_hits)]
        vector_hits: list[SearchHit] = []
        if self.settings.rag_hybrid_enabled and self.embedding_service is not None:
            try:
                query_vector = self.embedding_service.embed_query(
                    _embedding_query(query, original_query)
                )
                vector_hits = self.search_service.vector_search_many(
                    indices,
                    query_vector,
                    size=candidate_size,
                    filters=filters,
                )
            except Exception as exc:  # noqa: BLE001 - isolate the external provider.
                logger.warning(
                    "rag_vector_retrieval_failed agent=%s error=%s",
                    agent,
                    type(exc).__name__,
                )
        if vector_hits:
            rankings.append(("vector", vector_hits))

        fused_hits = reciprocal_rank_fusion(
            rankings,
            rank_constant=self.settings.rag_rrf_k,
        )
        rerank_result = self.reranker.rerank(
            original_query or query,
            fused_hits,
            size=candidate_size,
        )
        reranked_hits = rerank_result.hits
        hits = (
            self._diversify(reranked_hits, size=size, max_per_source=2)
            if agent == "recommend"
            else reranked_hits[:size]
        )
        mode = _retrieval_mode(
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
            indices=indices,
            hits=hits,
            mode=mode,
            candidate_counts={
                "bm25": len(lexical_hits),
                "vector": len(vector_hits),
                "fused": len(fused_hits),
            },
        )

    def _indices_for_agent(
        self,
        agent: str,
        capabilities: list[str] | None,
    ) -> list[str]:
        if agent == "recommend":
            return recommend_indices_for_capabilities(self.settings, capabilities)
        if agent == "support":
            return support_indices(self.settings)
        return []

    @staticmethod
    def _diversify(
        candidates: list[SearchHit],
        *,
        size: int,
        max_per_source: int,
    ) -> list[SearchHit]:
        selected: list[SearchHit] = []
        source_counts: dict[str, int] = {}
        seen_ids: set[str] = set()
        for hit in candidates:
            if hit.id in seen_ids:
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


def _embedding_query(query: str, original_query: str | None) -> str:
    if not original_query or original_query.strip() == query.strip():
        return query
    return f"{query}\n{original_query}"


def _retrieval_mode(*, has_vector: bool, rerank_applied: bool) -> str:
    base = "hybrid_rrf" if has_vector else "bm25"
    return f"{base}_rerank" if rerank_applied else base

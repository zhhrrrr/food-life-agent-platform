import logging
from time import monotonic

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.search.index_names import recommend_indices_for_capabilities, support_indices
from app.search.schemas import SearchFilters, SearchHit
from app.search.search_service import SearchService

logger = logging.getLogger("food_agent.rag.retriever")


class RetrievalResult(BaseModel):
    agent: str
    query: str
    indices: list[str] = Field(default_factory=list)
    hits: list[SearchHit] = Field(default_factory=list)

    @property
    def has_hits(self) -> bool:
        return bool(self.hits)


class RagRetriever:
    def __init__(
        self,
        settings: Settings | None = None,
        search_service: SearchService | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.search_service = search_service or SearchService(self.settings)

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

        candidate_size = min(max(size * 5, size), 50)
        candidates = self.search_service.search_many(
            indices,
            query,
            size=candidate_size,
            original_query=original_query,
            filters=filters,
        )
        hits = (
            self._diversify(candidates, size=size, max_per_source=2)
            if agent == "recommend"
            else candidates[:size]
        )
        logger.info(
            "rag_retrieval_completed agent=%s indices=%s candidates=%s hits=%s latency_ms=%.2f",
            agent,
            ",".join(indices),
            len(candidates),
            len(hits),
            (monotonic() - started) * 1000,
        )
        return RetrievalResult(agent=agent, query=query, indices=indices, hits=hits)

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

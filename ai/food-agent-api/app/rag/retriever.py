from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.search.index_names import recommend_indices, support_indices
from app.search.schemas import SearchHit
from app.search.search_service import SearchService


class RetrievalResult(BaseModel):
    agent: str
    query: str
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

    def retrieve(self, agent: str, query: str, size: int = 3) -> RetrievalResult:
        indices = self._indices_for_agent(agent)
        if not indices:
            return RetrievalResult(agent=agent, query=query)

        hits = self.search_service.search_many(indices, query, size=size)
        return RetrievalResult(agent=agent, query=query, hits=hits)

    def _indices_for_agent(self, agent: str) -> list[str]:
        if agent == "recommend":
            return recommend_indices(self.settings)
        if agent == "support":
            return support_indices(self.settings)
        return []

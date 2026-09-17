from app.core.config import Settings, get_settings
from app.search.index_names import recommend_indices, support_indices
from app.search.schemas import SearchHit
from app.search.search_service import SearchService


def retrieve_for_agent(
    agent: str,
    query: str,
    size: int = 3,
    settings: Settings | None = None,
    search_service: SearchService | None = None,
) -> list[SearchHit]:
    resolved_settings = settings or get_settings()
    service = search_service or SearchService(resolved_settings)

    if agent == "recommend":
        return service.search_many(recommend_indices(resolved_settings), query, size=size)
    if agent == "support":
        return service.search_many(support_indices(resolved_settings), query, size=size)

    return []

import logging

from fastapi import APIRouter, Body
from pydantic import BaseModel, Field

from app.core.errors import FoodAgentError
from app.indexing.indexer import IndexRebuildResult, SearchIndexer
from app.indexing.java_export_client import JavaSearchExportError

logger = logging.getLogger("food_agent.api.indexing")
router = APIRouter(prefix="/api/agent/indexes", tags=["indexes"])


class RebuildIndexesRequest(BaseModel):
    scopes: list[str] | None = Field(default=None)
    limit: int | None = Field(default=None, ge=1, le=1000)


class RebuildIndexesResponse(BaseModel):
    fetched: int
    indexed: int
    failed: int
    scopes: list[dict[str, int | str]]


@router.post("/rebuild", response_model=RebuildIndexesResponse)
def rebuild_indexes(
    request: RebuildIndexesRequest | None = Body(default=None),
) -> RebuildIndexesResponse:
    rebuild_request = request or RebuildIndexesRequest()

    try:
        result = SearchIndexer().rebuild(
            scopes=rebuild_request.scopes,
            limit=rebuild_request.limit,
        )
    except ValueError as exc:
        raise FoodAgentError(code="INDEX_SCOPE_INVALID", message=str(exc)) from exc
    except JavaSearchExportError as exc:
        logger.exception("java_search_export_failed")
        raise FoodAgentError(code="JAVA_SEARCH_EXPORT_FAILED", message=str(exc)) from exc

    return _to_response(result)


def _to_response(result: IndexRebuildResult) -> RebuildIndexesResponse:
    return RebuildIndexesResponse(
        fetched=result.fetched,
        indexed=result.indexed,
        failed=result.failed,
        scopes=[scope.model_dump() for scope in result.scopes],
    )

import logging

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.core.errors import FoodAgentError
from app.indexing.indexer import IndexRebuildResult, IndexReconcileResult, SearchIndexer
from app.indexing.java_export_client import JavaSearchExportError

logger = logging.getLogger("food_agent.api.indexing")
router = APIRouter(prefix="/api/agent/indexes", tags=["indexes"])


class RebuildIndexesRequest(BaseModel):
    scopes: list[str] | None = Field(default=None)
    page_size: int | None = Field(default=None, ge=1, le=1000)


class RebuildIndexesResponse(BaseModel):
    fetched: int
    indexed: int
    failed: int
    scopes: list[dict[str, int | str]]


class ReconcileIndexesRequest(BaseModel):
    scopes: list[str] | None = Field(default=None)
    page_size: int | None = Field(default=None, ge=1, le=1000)
    prune_stale: bool | None = Field(default=None)


class ReconcileIndexesResponse(BaseModel):
    fetched: int
    checked: int
    upserted: int
    unchanged: int
    deleted: int
    failed: int
    scopes: list[dict[str, int | str]]


@router.post("/rebuild", response_model=RebuildIndexesResponse)
def rebuild_indexes(
    request: RebuildIndexesRequest | None = None,
) -> RebuildIndexesResponse:
    rebuild_request = request or RebuildIndexesRequest()

    try:
        result = SearchIndexer().rebuild(
            scopes=rebuild_request.scopes,
            page_size=rebuild_request.page_size,
        )
    except ValueError as exc:
        raise FoodAgentError(code="INDEX_SCOPE_INVALID", message=str(exc)) from exc
    except JavaSearchExportError as exc:
        logger.exception("java_search_export_failed")
        raise FoodAgentError(code="JAVA_SEARCH_EXPORT_FAILED", message=str(exc)) from exc

    return _to_response(result)


@router.post("/reconcile", response_model=ReconcileIndexesResponse)
def reconcile_indexes(
    request: ReconcileIndexesRequest | None = None,
) -> ReconcileIndexesResponse:
    reconcile_request = request or ReconcileIndexesRequest()

    try:
        result = SearchIndexer().reconcile(
            scopes=reconcile_request.scopes,
            page_size=reconcile_request.page_size,
            prune_stale=reconcile_request.prune_stale,
        )
    except ValueError as exc:
        raise FoodAgentError(code="INDEX_SCOPE_INVALID", message=str(exc)) from exc
    except JavaSearchExportError as exc:
        logger.exception("java_search_export_failed")
        raise FoodAgentError(code="JAVA_SEARCH_EXPORT_FAILED", message=str(exc)) from exc

    return _to_reconcile_response(result)


def _to_response(result: IndexRebuildResult) -> RebuildIndexesResponse:
    return RebuildIndexesResponse(
        fetched=result.fetched,
        indexed=result.indexed,
        failed=result.failed,
        scopes=[scope.model_dump() for scope in result.scopes],
    )


def _to_reconcile_response(result: IndexReconcileResult) -> ReconcileIndexesResponse:
    return ReconcileIndexesResponse(
        fetched=result.fetched,
        checked=result.checked,
        upserted=result.upserted,
        unchanged=result.unchanged,
        deleted=result.deleted,
        failed=result.failed,
        scopes=[scope.model_dump() for scope in result.scopes],
    )

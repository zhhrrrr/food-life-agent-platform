from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.indexing.java_export_client import JavaSearchExportClient
from app.search.index_names import SEARCH_SCOPES, index_name_for_scope
from app.search.search_service import SearchService


class IndexScopeResult(BaseModel):
    scope: str
    index: str
    fetched: int = 0
    indexed: int = 0
    failed: int = 0


class IndexRebuildResult(BaseModel):
    scopes: list[IndexScopeResult] = Field(default_factory=list)

    @property
    def fetched(self) -> int:
        return sum(scope.fetched for scope in self.scopes)

    @property
    def indexed(self) -> int:
        return sum(scope.indexed for scope in self.scopes)

    @property
    def failed(self) -> int:
        return sum(scope.failed for scope in self.scopes)


class SearchIndexer:
    def __init__(
        self,
        export_client: JavaSearchExportClient | None = None,
        search_service: SearchService | None = None,
        settings: Settings | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.export_client = export_client or JavaSearchExportClient(settings=self.settings)
        self.search_service = search_service or SearchService(settings=self.settings)

    def rebuild(
        self,
        scopes: list[str] | None = None,
        limit: int | None = None,
    ) -> IndexRebuildResult:
        scope_results: list[IndexScopeResult] = []

        for scope in self._normalize_scopes(scopes):
            index = index_name_for_scope(self.settings, scope)
            documents = self.export_client.fetch_documents(scope=scope, limit=limit)
            indexed, failed = self.search_service.index_documents(
                index=index,
                documents=documents,
            )
            scope_results.append(
                IndexScopeResult(
                    scope=scope,
                    index=index,
                    fetched=len(documents),
                    indexed=indexed,
                    failed=failed,
                )
            )

        return IndexRebuildResult(scopes=scope_results)

    @staticmethod
    def _normalize_scopes(scopes: list[str] | None) -> list[str]:
        if not scopes:
            return list(SEARCH_SCOPES)

        unknown_scopes = [scope for scope in scopes if scope not in SEARCH_SCOPES]
        if unknown_scopes:
            raise ValueError(f"unsupported search scopes: {', '.join(unknown_scopes)}")

        return list(dict.fromkeys(scopes))

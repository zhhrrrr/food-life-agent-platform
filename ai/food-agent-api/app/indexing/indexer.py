import json
import logging

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.indexing.java_export_client import JavaSearchExportClient
from app.indexing.rule_document_source import list_rule_documents
from app.search.index_names import SEARCH_SCOPES, index_name_for_scope
from app.search.schemas import SearchDocument
from app.search.search_service import SearchService

logger = logging.getLogger("food_agent.indexing")


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


class IndexReconcileScopeResult(BaseModel):
    scope: str
    index: str
    fetched: int = 0
    checked: int = 0
    upserted: int = 0
    unchanged: int = 0
    deleted: int = 0
    failed: int = 0


class IndexReconcileResult(BaseModel):
    scopes: list[IndexReconcileScopeResult] = Field(default_factory=list)

    @property
    def fetched(self) -> int:
        return sum(scope.fetched for scope in self.scopes)

    @property
    def checked(self) -> int:
        return sum(scope.checked for scope in self.scopes)

    @property
    def upserted(self) -> int:
        return sum(scope.upserted for scope in self.scopes)

    @property
    def unchanged(self) -> int:
        return sum(scope.unchanged for scope in self.scopes)

    @property
    def deleted(self) -> int:
        return sum(scope.deleted for scope in self.scopes)

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
        page_size: int | None = None,
    ) -> IndexRebuildResult:
        scope_results: list[IndexScopeResult] = []

        for scope in self._normalize_scopes(scopes):
            alias = index_name_for_scope(self.settings, scope)
            documents = self._fetch_all_documents(
                scope=scope,
                page_size=page_size or self.settings.es_reconcile_page_size,
            )
            physical_index = self.search_service.create_rebuild_index(alias, scope)
            try:
                indexed, failed = self.search_service.index_documents(
                    index=physical_index,
                    documents=documents,
                )
                self.search_service.refresh_index(physical_index)
                stored = self.search_service.count_documents(physical_index)
                if failed or indexed != len(documents) or stored != len(documents):
                    logger.error(
                        "es_rebuild_validation_failed scope=%s fetched=%s indexed=%s stored=%s failed=%s",
                        scope,
                        len(documents),
                        indexed,
                        stored,
                        failed,
                    )
                    self.search_service.discard_index(physical_index)
                    validation_failures = max(
                        failed,
                        len(documents) - min(indexed, stored),
                        1,
                    )
                    scope_results.append(
                        IndexScopeResult(
                            scope=scope,
                            index=alias,
                            fetched=len(documents),
                            indexed=min(indexed, stored),
                            failed=validation_failures,
                        )
                    )
                    continue
                self.search_service.promote_index(alias, physical_index)
            except Exception:
                self.search_service.discard_index(physical_index)
                raise
            try:
                self.search_service.cleanup_old_indices(
                    alias,
                    current_index=physical_index,
                    keep=self.settings.es_rebuild_keep_versions,
                )
            except Exception:
                logger.exception("es_old_index_cleanup_failed scope=%s", scope)
            scope_results.append(
                IndexScopeResult(
                    scope=scope,
                    index=alias,
                    fetched=len(documents),
                    indexed=indexed,
                    failed=failed,
                )
            )

        return IndexRebuildResult(scopes=scope_results)

    def reconcile(
        self,
        scopes: list[str] | None = None,
        page_size: int | None = None,
        prune_stale: bool | None = None,
    ) -> IndexReconcileResult:
        scope_results: list[IndexReconcileScopeResult] = []
        normalized_page_size = self._normalize_page_size(page_size or self.settings.es_reconcile_page_size)
        should_prune_stale = (
            self.settings.es_reconcile_prune_stale if prune_stale is None else prune_stale
        )

        for scope in self._normalize_scopes(scopes):
            scope_results.append(
                self._reconcile_scope(
                    scope=scope,
                    page_size=normalized_page_size,
                    prune_stale=should_prune_stale,
                )
            )

        return IndexReconcileResult(scopes=scope_results)

    def sync_document(
        self,
        scope: str,
        entity_id: int | str,
        entity_type: str | None = None,
        action: str = "UPSERT",
    ) -> IndexScopeResult:
        self._normalize_scopes([scope])
        if scope == "rules":
            raise ValueError("rules scope does not support single-document sync; use rebuild/reconcile")
        index = index_name_for_scope(self.settings, scope)
        normalized_action = action.upper()

        if normalized_action == "DELETE":
            deleted = self.search_service.delete_document(
                index=index,
                document_id=self._document_id(scope, entity_id, entity_type),
            )
            return IndexScopeResult(
                scope=scope,
                index=index,
                fetched=0,
                indexed=0,
                failed=0 if deleted else 1,
            )

        document = self.export_client.fetch_document(
            scope=scope,
            entity_id=entity_id,
            entity_type=entity_type,
        )
        if document is None:
            deleted = self.search_service.delete_document(
                index=index,
                document_id=self._document_id(scope, entity_id, entity_type),
            )
            return IndexScopeResult(
                scope=scope,
                index=index,
                fetched=0,
                indexed=0,
                failed=0 if deleted else 1,
            )

        indexed = self.search_service.index_document(index=index, document=document)
        return IndexScopeResult(
            scope=scope,
            index=index,
            fetched=1,
            indexed=1 if indexed else 0,
            failed=0 if indexed else 1,
        )

    @staticmethod
    def _normalize_scopes(scopes: list[str] | None) -> list[str]:
        if not scopes:
            return list(SEARCH_SCOPES)

        unknown_scopes = [scope for scope in scopes if scope not in SEARCH_SCOPES]
        if unknown_scopes:
            raise ValueError(f"unsupported search scopes: {', '.join(unknown_scopes)}")

        return list(dict.fromkeys(scopes))

    def _reconcile_scope(
        self,
        scope: str,
        page_size: int,
        prune_stale: bool,
    ) -> IndexReconcileScopeResult:
        index = index_name_for_scope(self.settings, scope)
        result = IndexReconcileScopeResult(scope=scope, index=index)
        expected_ids: set[str] = set()
        offset = 0

        while True:
            documents = self._fetch_documents(
                scope=scope,
                limit=page_size,
                offset=offset,
            )
            if not documents:
                break

            result.fetched += len(documents)
            for document in documents:
                expected_ids.add(document.id)
                result.checked += 1
                existing = self.search_service.get_document(index=index, document_id=document.id)
                if existing is not None and self._document_equal(existing, document):
                    result.unchanged += 1
                    continue
                if self.search_service.index_document(index=index, document=document):
                    result.upserted += 1
                else:
                    result.failed += 1

            if len(documents) < page_size:
                break
            offset += page_size

        if prune_stale:
            for document_id in self.search_service.list_document_ids(
                index=index,
                size=page_size,
            ):
                if document_id in expected_ids:
                    continue
                if self.search_service.delete_document(index=index, document_id=document_id):
                    result.deleted += 1
                else:
                    result.failed += 1

        return result

    def _fetch_documents(
        self,
        scope: str,
        limit: int | None = None,
        offset: int | None = None,
    ) -> list[SearchDocument]:
        if scope == "rules":
            return list_rule_documents(
                limit=limit,
                offset=offset,
                settings=self.settings,
            )
        return self.export_client.fetch_documents(
            scope=scope,
            limit=limit,
            offset=offset,
        )

    def _fetch_all_documents(self, scope: str, page_size: int) -> list[SearchDocument]:
        normalized_page_size = self._normalize_page_size(page_size)
        documents: list[SearchDocument] = []
        offset = 0
        while True:
            page = self._fetch_documents(
                scope=scope,
                limit=normalized_page_size,
                offset=offset,
            )
            documents.extend(page)
            if len(page) < normalized_page_size:
                break
            offset += normalized_page_size
        return documents

    @staticmethod
    def _normalize_page_size(page_size: int) -> int:
        return max(1, min(page_size, 1000))

    @staticmethod
    def _document_equal(left: SearchDocument, right: SearchDocument) -> bool:
        return json.dumps(left.model_dump(mode="json"), sort_keys=True, default=str) == json.dumps(
            right.model_dump(mode="json"),
            sort_keys=True,
            default=str,
        )

    @staticmethod
    def _document_id(scope: str, entity_id: int | str, entity_type: str | None = None) -> str:
        if scope == "shops":
            return f"shop-{entity_id}"
        if scope == "packages":
            return f"package-{entity_id}"
        if scope == "reviews":
            return f"review-{entity_id}"
        if scope == "coupons":
            return f"coupon-template-{entity_id}"
        if scope == "activities":
            normalized_type = (entity_type or "").upper()
            if normalized_type == "GROUP_BUY":
                return f"group-buy-activity-{entity_id}"
            if normalized_type == "SECKILL":
                return f"seckill-activity-{entity_id}"
        raise ValueError(f"unsupported document id scope/type: {scope}/{entity_type}")

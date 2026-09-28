from app.core.config import Settings
from app.indexing.indexer import SearchIndexer
from app.search.schemas import SearchDocument


class FakeExportClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int | None, int | None]] = []
        self.document_calls: list[tuple[str, int | str, str | None]] = []
        self.documents_by_scope: dict[str, list[SearchDocument]] = {}

    def fetch_documents(
        self,
        scope: str,
        limit: int | None = None,
        offset: int | None = None,
    ) -> list[SearchDocument]:
        self.calls.append((scope, limit, offset))
        documents = self.documents_by_scope.get(scope) or [
            SearchDocument(
                id=f"{scope}-1",
                title=f"{scope} title",
                content=f"{scope} content",
                source=f"food_{scope}_index",
            )
        ]
        if offset is None:
            offset = 0
        if limit is None:
            return documents[offset:]
        return documents[offset : offset + limit]

    def fetch_document(
        self,
        scope: str,
        entity_id: int | str,
        entity_type: str | None = None,
    ) -> SearchDocument | None:
        self.document_calls.append((scope, entity_id, entity_type))
        if scope == "coupons" and entity_id == "missing":
            return None
        return SearchDocument(
            id=f"{scope}-{entity_id}",
            title=f"{scope} title",
            content=f"{scope} content",
            source=f"food_{scope}_index",
        )


class FakeSearchService:
    def __init__(self) -> None:
        self.calls: list[tuple[str, list[str]]] = []
        self.single_calls: list[tuple[str, str]] = []
        self.delete_calls: list[tuple[str, str]] = []
        self.documents: dict[str, dict[str, SearchDocument]] = {}
        self.ids_by_index: dict[str, set[str]] = {}
        self.created: list[tuple[str, str, str]] = []
        self.promoted: list[tuple[str, str]] = []
        self.discarded: list[str] = []
        self.cleaned: list[tuple[str, str, int]] = []
        self.bulk_failure = False

    def create_rebuild_index(self, alias: str, scope: str) -> str:
        physical_index = f"{alias}_v1"
        self.created.append((alias, scope, physical_index))
        return physical_index

    def promote_index(self, alias: str, physical_index: str) -> None:
        self.promoted.append((alias, physical_index))

    def discard_index(self, index: str) -> None:
        self.discarded.append(index)

    def cleanup_old_indices(self, alias: str, current_index: str, keep: int) -> None:
        self.cleaned.append((alias, current_index, keep))

    def refresh_index(self, index: str) -> None:
        return None

    def count_documents(self, index: str) -> int:
        return len(self.documents.get(index, {}))

    def index_documents(
        self,
        index: str,
        documents: list[SearchDocument],
    ) -> tuple[int, int]:
        self.calls.append((index, [document.id for document in documents]))
        if self.bulk_failure:
            return 0, len(documents)
        self.documents.setdefault(index, {}).update({document.id: document for document in documents})
        return len(documents), 0

    def index_document(self, index: str, document: SearchDocument) -> bool:
        self.single_calls.append((index, document.id))
        self.documents.setdefault(index, {})[document.id] = document
        return True

    def delete_document(self, index: str, document_id: str) -> bool:
        self.delete_calls.append((index, document_id))
        self.documents.setdefault(index, {}).pop(document_id, None)
        return True

    def get_document(self, index: str, document_id: str) -> SearchDocument | None:
        return self.documents.get(index, {}).get(document_id)

    def list_document_ids(self, index: str, size: int = 10000) -> set[str]:
        if index in self.ids_by_index:
            return self.ids_by_index[index]
        return set(self.documents.get(index, {}).keys())


def test_indexer_rebuilds_selected_scopes() -> None:
    settings = Settings()
    export_client = FakeExportClient()
    search_service = FakeSearchService()
    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=settings,
    )

    result = indexer.rebuild(scopes=["shops", "coupons"], page_size=50)

    assert result.fetched == 2
    assert result.indexed == 2
    assert result.failed == 0
    assert export_client.calls == [("shops", 50, 0), ("coupons", 50, 0)]
    assert search_service.calls == [
        ("food_shop_index_v1", ["shops-1"]),
        ("food_coupon_index_v1", ["coupons-1"]),
    ]
    assert search_service.promoted == [
        ("food_shop_index", "food_shop_index_v1"),
        ("food_coupon_index", "food_coupon_index_v1"),
    ]
    assert search_service.cleaned == [
        ("food_shop_index", "food_shop_index_v1", 2),
        ("food_coupon_index", "food_coupon_index_v1", 2),
    ]


def test_indexer_rejects_unknown_scope() -> None:
    indexer = SearchIndexer(
        export_client=FakeExportClient(),
        search_service=FakeSearchService(),
        settings=Settings(),
    )

    try:
        indexer.rebuild(scopes=["orders"])
    except ValueError as exc:
        assert "unsupported search scopes: orders" in str(exc)
    else:
        raise AssertionError("expected ValueError")


def test_indexer_does_not_switch_alias_when_bulk_validation_fails() -> None:
    search_service = FakeSearchService()
    search_service.bulk_failure = True
    indexer = SearchIndexer(
        export_client=FakeExportClient(),
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.rebuild(scopes=["shops"], page_size=50)

    assert result.failed == 1
    assert search_service.promoted == []
    assert search_service.discarded == ["food_shop_index_v1"]


def test_indexer_rebuilds_seeded_rule_documents_without_java_export() -> None:
    export_client = FakeExportClient()
    search_service = FakeSearchService()
    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.rebuild(scopes=["rules"], page_size=3)

    assert result.fetched >= 3
    assert result.indexed == result.fetched
    assert result.failed == 0
    assert export_client.calls == []
    assert search_service.calls[0][0] == "food_rule_index_v1"
    assert search_service.calls[0][1][:3] == [
        "rule-refund-apply-window",
        "rule-refund-payment-route",
        "rule-refund-coupon-return",
    ]
    assert (
        search_service.documents["food_rule_index_v1"]["rule-refund-apply-window"].metadata[
            "doc_type"
        ]
        == "platform_rule"
    )


def test_indexer_rejects_single_rule_document_sync() -> None:
    indexer = SearchIndexer(
        export_client=FakeExportClient(),
        search_service=FakeSearchService(),
        settings=Settings(),
    )

    try:
        indexer.sync_document(scope="rules", entity_id="refund-apply-window")
    except ValueError as exc:
        assert "rules scope does not support single-document sync" in str(exc)
    else:
        raise AssertionError("expected ValueError")


def test_indexer_syncs_single_document() -> None:
    export_client = FakeExportClient()
    search_service = FakeSearchService()
    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.sync_document(scope="activities", entity_id=7, entity_type="SECKILL")

    assert result.fetched == 1
    assert result.indexed == 1
    assert export_client.document_calls == [("activities", 7, "SECKILL")]
    assert search_service.single_calls == [("food_activity_index", "activities-7")]


def test_indexer_deletes_missing_document() -> None:
    export_client = FakeExportClient()
    search_service = FakeSearchService()
    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.sync_document(scope="coupons", entity_id="missing")

    assert result.fetched == 0
    assert result.indexed == 0
    assert result.failed == 0
    assert search_service.delete_calls == [("food_coupon_index", "coupon-template-missing")]


def test_indexer_reconciles_pages_and_prunes_stale_documents() -> None:
    export_client = FakeExportClient()
    export_client.documents_by_scope["shops"] = [
        SearchDocument(
            id="shop-1",
            title="Shop 1",
            content="New content",
            source="food_shop_index",
        ),
        SearchDocument(
            id="shop-2",
            title="Shop 2",
            content="Fresh",
            source="food_shop_index",
        ),
    ]
    search_service = FakeSearchService()
    search_service.documents["food_shop_index"] = {
        "shop-1": SearchDocument(
            id="shop-1",
            title="Shop 1",
            content="Old content",
            source="food_shop_index",
        ),
        "shop-stale": SearchDocument(
            id="shop-stale",
            title="Closed",
            content="Closed",
            source="food_shop_index",
        ),
    }

    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.reconcile(scopes=["shops"], page_size=1, prune_stale=True)

    assert result.fetched == 2
    assert result.checked == 2
    assert result.upserted == 2
    assert result.unchanged == 0
    assert result.deleted == 1
    assert result.failed == 0
    assert export_client.calls == [
        ("shops", 1, 0),
        ("shops", 1, 1),
        ("shops", 1, 2),
    ]
    assert search_service.single_calls == [
        ("food_shop_index", "shop-1"),
        ("food_shop_index", "shop-2"),
    ]
    assert search_service.delete_calls == [("food_shop_index", "shop-stale")]


def test_indexer_reconcile_keeps_unchanged_document() -> None:
    document = SearchDocument(
        id="coupon-template-1",
        title="Coupon",
        content="Discount",
        source="food_coupon_index",
    )
    export_client = FakeExportClient()
    export_client.documents_by_scope["coupons"] = [document]
    search_service = FakeSearchService()
    search_service.documents["food_coupon_index"] = {"coupon-template-1": document}

    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=Settings(),
    )

    result = indexer.reconcile(scopes=["coupons"], page_size=100, prune_stale=False)

    assert result.fetched == 1
    assert result.upserted == 0
    assert result.unchanged == 1
    assert result.deleted == 0
    assert search_service.single_calls == []
    assert search_service.delete_calls == []

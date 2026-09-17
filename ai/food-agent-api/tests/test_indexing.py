from app.core.config import Settings
from app.indexing.indexer import SearchIndexer
from app.search.schemas import SearchDocument


class FakeExportClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int | None]] = []
        self.document_calls: list[tuple[str, int | str, str | None]] = []

    def fetch_documents(
        self,
        scope: str,
        limit: int | None = None,
    ) -> list[SearchDocument]:
        self.calls.append((scope, limit))
        return [
            SearchDocument(
                id=f"{scope}-1",
                title=f"{scope} title",
                content=f"{scope} content",
                source=f"food_{scope}_index",
            )
        ]

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

    def index_documents(
        self,
        index: str,
        documents: list[SearchDocument],
    ) -> tuple[int, int]:
        self.calls.append((index, [document.id for document in documents]))
        return len(documents), 0

    def index_document(self, index: str, document: SearchDocument) -> bool:
        self.single_calls.append((index, document.id))
        return True

    def delete_document(self, index: str, document_id: str) -> bool:
        self.delete_calls.append((index, document_id))
        return True


def test_indexer_rebuilds_selected_scopes() -> None:
    settings = Settings()
    export_client = FakeExportClient()
    search_service = FakeSearchService()
    indexer = SearchIndexer(
        export_client=export_client,
        search_service=search_service,
        settings=settings,
    )

    result = indexer.rebuild(scopes=["shops", "coupons"], limit=50)

    assert result.fetched == 2
    assert result.indexed == 2
    assert result.failed == 0
    assert export_client.calls == [("shops", 50), ("coupons", 50)]
    assert search_service.calls == [
        ("food_shop_index", ["shops-1"]),
        ("food_coupon_index", ["coupons-1"]),
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

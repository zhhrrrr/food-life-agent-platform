from app.core.config import Settings
from app.indexing.indexer import SearchIndexer
from app.search.schemas import SearchDocument


class FakeExportClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int | None]] = []

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


class FakeSearchService:
    def __init__(self) -> None:
        self.calls: list[tuple[str, list[str]]] = []

    def index_documents(
        self,
        index: str,
        documents: list[SearchDocument],
    ) -> tuple[int, int]:
        self.calls.append((index, [document.id for document in documents]))
        return len(documents), 0


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

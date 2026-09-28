from typing import Any

from app.core.config import Settings
from app.search.schemas import SearchDocument, SearchFilters
from app.vector.collection_names import collection_aliases_for_scopes
from app.vector.milvus_service import MilvusVectorService


class FakeIterator:
    def __init__(self, pages: list[list[dict[str, str]]]) -> None:
        self.pages = pages
        self.closed = False

    def next(self) -> list[dict[str, str]]:
        return self.pages.pop(0)

    def close(self) -> None:
        self.closed = True


class FakeMilvusClient:
    def __init__(self) -> None:
        self.inserted: list[dict[str, Any]] = []
        self.search_request: dict[str, Any] | None = None
        self.aliases: dict[str, str] = {}
        self.create_request: dict[str, Any] | None = None

    def create_collection(self, **kwargs: Any) -> None:
        self.create_request = kwargs

    def insert(self, **kwargs: Any) -> dict[str, int]:
        self.inserted.extend(kwargs["data"])
        return {"insert_count": len(kwargs["data"])}

    def flush(self, **kwargs: Any) -> None:
        return None

    def search(self, **kwargs: Any) -> list[list[dict[str, Any]]]:
        self.search_request = kwargs
        return [[
            {
                "id": "package-1",
                "distance": 0.91,
                "entity": {
                    "id": "package-1",
                    "title": "Two-person dinner",
                    "content": "Hot pot set",
                    "source": "food_package_index",
                    "metadata": {"price": 9900},
                },
            },
            {
                "id": "package-noise",
                "distance": 0.1,
                "entity": {
                    "id": "package-noise",
                    "title": "Noise",
                    "content": "Unrelated",
                    "source": "food_package_index",
                    "metadata": {},
                },
            },
        ]]

    def query(self, **kwargs: Any) -> list[dict[str, str]]:
        return []

    def query_iterator(self, **kwargs: Any) -> FakeIterator:
        return FakeIterator([[{"id": "package-1"}], []])

    def describe_alias(self, *, alias: str, **kwargs: Any) -> dict[str, str]:
        if alias not in self.aliases:
            raise RuntimeError("alias missing")
        return {"collection_name": self.aliases[alias]}

    def create_alias(self, *, alias: str, collection_name: str, **kwargs: Any) -> None:
        self.aliases[alias] = collection_name

    def alter_alias(self, *, alias: str, collection_name: str, **kwargs: Any) -> None:
        self.aliases[alias] = collection_name

    def load_collection(self, **kwargs: Any) -> None:
        return None


def _document() -> SearchDocument:
    return SearchDocument(
        id="package-1",
        title="Two-person dinner",
        content="Hot pot set",
        source="food_package_index",
        category="hot pot",
        metadata={
            "price": 9900,
            "status": 1,
            "validStartTime": "2026-01-01T00:00:00+08:00",
        },
    )


def test_milvus_collection_has_vector_and_reconciliation_fields() -> None:
    client = FakeMilvusClient()
    service = MilvusVectorService(
        Settings(milvus_enabled=True, rag_embedding_dimensions=3),
        client=client,
    )

    collection = service.create_rebuild_collection("food_package_vectors")

    assert collection.startswith("food_package_vectors_v")
    assert client.create_request is not None
    schema = client.create_request["schema"]
    assert {field.name for field in schema.fields} == {
        "id",
        "embedding",
        "title",
        "content",
        "source",
        "metadata",
        "content_hash",
        "status",
        "valid_start",
        "valid_end",
        "price",
    }
    embedding = next(field for field in schema.fields if field.name == "embedding")
    assert embedding.params["dim"] == 3


def test_milvus_rows_store_vectors_and_filterable_business_fields() -> None:
    client = FakeMilvusClient()
    service = MilvusVectorService(
        Settings(milvus_enabled=True, rag_embedding_dimensions=3),
        client=client,
    )

    assert service.index_documents("food_package_vectors_v1", [_document()], [[1.0, 0.0, 0.0]]) == (1, 0)

    row = client.inserted[0]
    assert row["embedding"] == [1.0, 0.0, 0.0]
    assert row["status"] == 1
    assert row["price"] == 9900
    assert len(row["content_hash"]) == 64
    assert row["valid_start"] > 0


def test_milvus_search_uses_alias_cosine_and_scalar_filters() -> None:
    client = FakeMilvusClient()
    service = MilvusVectorService(Settings(milvus_enabled=True), client=client)

    hits = service.search_many(
        ["food_package_vectors"],
        [1.0, 0.0],
        size=5,
        filters=SearchFilters(max_price_cents=10_000),
    )

    assert hits[0].id == "package-1"
    assert [hit.id for hit in hits] == ["package-1"]
    assert hits[0].score == 0.91
    assert client.search_request is not None
    assert client.search_request["collection_name"] == "food_package_vectors"
    assert client.search_request["search_params"] == {"metric_type": "COSINE"}
    assert "price <= 10000" in client.search_request["filter"]
    assert "valid_end" in client.search_request["filter"]


def test_milvus_alias_promotes_new_collection_and_returns_previous() -> None:
    client = FakeMilvusClient()
    client.aliases["food_shop_vectors"] = "food_shop_vectors_v1"
    service = MilvusVectorService(Settings(milvus_enabled=True), client=client)

    previous = service.promote_collection("food_shop_vectors", "food_shop_vectors_v2")

    assert previous == "food_shop_vectors_v1"
    assert client.aliases["food_shop_vectors"] == "food_shop_vectors_v2"


def test_collection_aliases_follow_selected_scopes() -> None:
    aliases = collection_aliases_for_scopes(
        Settings(),
        ["reviews", "shops", "reviews"],
    )

    assert aliases == ["food_review_vectors", "food_shop_vectors"]

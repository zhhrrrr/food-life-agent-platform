from typing import Any

from app.core.config import Settings
from app.search.index_schema import index_definition
from app.search.schemas import SearchFilters
from app.search.search_service import SearchService


def test_search_uses_only_text_fields(monkeypatch) -> None:
    class FakeElasticsearch:
        query: dict[str, Any] | None = None
        request: dict[str, Any] | None = None

        def search(self, **kwargs: Any) -> dict[str, Any]:
            self.request = kwargs
            self.query = kwargs["query"]
            return {"hits": {"hits": []}}

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    hits = SearchService(Settings(es_enabled=True)).search(
        index="food_shop_index",
        query="hot pot dinner",
    )

    assert hits == []
    assert client.request is not None
    assert client.request["index"] == "food_shop_index"
    assert client.request["search_type"] == "dfs_query_then_fetch"
    text_query = client.query["bool"]["should"][0]["multi_match"]
    assert text_query == {
        "query": "hot pot dinner",
        "fields": ["title^3", "content", "category"],
        "type": "best_fields",
        "minimum_should_match": "30%",
        "boost": 1.0,
    }


def test_search_combines_indices_queries_and_budget_in_one_request(monkeypatch) -> None:
    class FakeElasticsearch:
        request: dict[str, Any] | None = None

        def search(self, **kwargs: Any) -> dict[str, Any]:
            self.request = kwargs
            return {"hits": {"hits": []}}

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    SearchService(Settings(es_enabled=True)).search_many(
        ["food_shop_index", "food_package_index"],
        "hot pot under 200 yuan",
        original_query="Recommend hot pot with a budget of 200 yuan",
        filters=SearchFilters(max_price_cents=20_000),
        size=5,
    )

    assert client.request is not None
    assert client.request["index"] == "food_shop_index,food_package_index"
    bool_query = client.request["query"]["bool"]
    assert len(bool_query["should"]) == 2
    assert any(
        clause.get("bool", {}).get("should", [{}])[0]
        == {"range": {"metadata.price": {"lte": 20_000}}}
        for clause in bool_query["filter"]
    )


def test_index_definition_is_strict_and_uses_cjk_text_mapping() -> None:
    definition = index_definition("packages", shards=1, replicas=0)

    assert definition["settings"] == {
        "number_of_shards": 1,
        "number_of_replicas": 0,
    }
    mappings = definition["mappings"]
    assert mappings["dynamic"] == "strict"
    assert mappings["properties"]["title"]["analyzer"] == "cjk"
    assert mappings["properties"]["metadata"]["dynamic"] is False
    assert mappings["properties"]["metadata"]["properties"]["price"] == {
        "type": "long"
    }


def test_promote_index_atomically_replaces_legacy_concrete_index(monkeypatch) -> None:
    class FakeIndices:
        actions: list[dict[str, Any]] | None = None

        def exists_alias(self, *, name: str) -> bool:
            assert name == "food_shop_index"
            return False

        def exists(self, *, index: str) -> bool:
            assert index == "food_shop_index"
            return True

        def update_aliases(self, *, actions: list[dict[str, Any]]) -> None:
            self.actions = actions

    class FakeElasticsearch:
        indices = FakeIndices()

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    SearchService(Settings(es_enabled=True)).promote_index(
        "food_shop_index",
        "food_shop_index_v2",
    )

    assert client.indices.actions == [
        {"remove_index": {"index": "food_shop_index"}},
        {
            "add": {
                "index": "food_shop_index_v2",
                "alias": "food_shop_index",
                "is_write_index": True,
            }
        },
    ]


def test_list_document_ids_uses_pit_and_search_after(monkeypatch) -> None:
    class FakeElasticsearch:
        def __init__(self) -> None:
            self.requests: list[dict[str, Any]] = []
            self.closed_id = ""

        def open_point_in_time(self, *, index: str, keep_alive: str) -> dict[str, str]:
            assert index == "food_shop_index"
            assert keep_alive == "1m"
            return {"id": "pit-1"}

        def search(self, **kwargs: Any) -> dict[str, Any]:
            self.requests.append(kwargs)
            if len(self.requests) == 1:
                return {
                    "pit_id": "pit-2",
                    "hits": {
                        "hits": [
                            {"_id": "shop-1", "sort": [1]},
                            {"_id": "shop-2", "sort": [2]},
                        ]
                    },
                }
            return {
                "pit_id": "pit-3",
                "hits": {"hits": [{"_id": "shop-3", "sort": [3]}]},
            }

        def close_point_in_time(self, *, id: str) -> None:
            self.closed_id = id

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    ids = SearchService(Settings(es_enabled=True)).list_document_ids(
        "food_shop_index",
        size=2,
    )

    assert ids == {"shop-1", "shop-2", "shop-3"}
    assert client.requests[1]["search_after"] == [2]
    assert client.closed_id == "pit-3"


def test_cleanup_old_indices_keeps_current_and_one_rollback(monkeypatch) -> None:
    class FakeIndices:
        def __init__(self) -> None:
            self.deleted: list[str] = []

        def get_alias(self, *, index: str) -> dict[str, Any]:
            assert index == "food_shop_index_v*"
            return {
                "food_shop_index_v3": {},
                "food_shop_index_v2": {},
                "food_shop_index_v1": {},
            }

        def delete(self, *, index: str) -> None:
            self.deleted.append(index)

    class FakeElasticsearch:
        indices = FakeIndices()

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    SearchService(Settings(es_enabled=True)).cleanup_old_indices(
        "food_shop_index",
        current_index="food_shop_index_v3",
        keep=2,
    )

    assert client.indices.deleted == ["food_shop_index_v1"]

from typing import Any

from app.core.config import Settings
from app.search.search_service import SearchService


def test_search_uses_only_text_fields(monkeypatch) -> None:
    class FakeElasticsearch:
        query: dict[str, Any] | None = None

        def search(self, **kwargs: Any) -> dict[str, Any]:
            self.query = kwargs["query"]
            return {"hits": {"hits": []}}

    client = FakeElasticsearch()
    monkeypatch.setattr("app.search.search_service.get_es_client", lambda: client)

    hits = SearchService(Settings(es_enabled=True)).search(
        index="food_shop_index",
        query="hot pot dinner",
    )

    assert hits == []
    assert client.query == {
        "multi_match": {
            "query": "hot pot dinner",
            "fields": ["title^3", "content", "category", "tags"],
        }
    }

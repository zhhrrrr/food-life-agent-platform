from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app
from app.rag.context_builder import build_context_summary, build_references
from app.rag.retriever import retrieve_for_agent
from app.search.schemas import SearchHit


client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert "X-Request-Id" in response.headers


def test_settings_version() -> None:
    settings = get_settings()

    assert settings.app_version == "0.4.0"
    assert settings.es_enabled is False
    assert settings.es_index_shop == "food_shop_index"


def test_chat_routes_to_recommend_agent() -> None:
    message = "\u5e2e\u6211\u63a8\u8350\u4e00\u5bb6\u5ddd\u83dc\u9986"
    response = client.post("/api/agent/chat", json={"message": message})

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "recommend"
    assert data["agent"] == "recommend"
    assert data["response"] == f"Recommend Agent \u6536\u5230\u8bf7\u6c42\uff1a{message}"
    assert data["references"] == []


def test_chat_routes_to_trade_agent() -> None:
    message = "\u5e2e\u6211\u67e5\u8be2\u4e00\u4e0b\u8fd9\u4e2a\u8ba2\u5355"
    response = client.post("/api/agent/chat", json={"message": message})

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "trade"
    assert data["agent"] == "trade"
    assert data["response"] == f"Trade Agent \u6536\u5230\u8bf7\u6c42\uff1a{message}"
    assert data["references"] == []


def test_chat_routes_to_support_agent() -> None:
    message = "\u9000\u6b3e\u89c4\u5219\u662f\u4ec0\u4e48"
    response = client.post("/api/agent/chat", json={"message": message})

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "support"
    assert data["agent"] == "support"
    assert data["response"] == f"Support Agent \u6536\u5230\u8bf7\u6c42\uff1a{message}"
    assert data["references"] == []


def test_request_id_can_be_provided_by_client() -> None:
    request_id = "stage-1-test-request"
    response = client.get("/health", headers={"X-Request-Id": request_id})

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == request_id


def test_retriever_returns_empty_when_es_disabled() -> None:
    settings = get_settings()

    hits = retrieve_for_agent("recommend", "川菜", settings=settings)

    assert hits == []


def test_context_builder_creates_summary_and_references() -> None:
    hits = [
        SearchHit(
            id="shop-1",
            title="Shu Xiang",
            content="Sichuan restaurant for team dinners.",
            source="food_shop_index",
            score=3.5,
        )
    ]

    context = build_context_summary(hits)
    references = build_references(hits)

    assert context == "1. Shu Xiang: Sichuan restaurant for team dinners."
    assert references[0].id == "shop-1"
    assert references[0].source == "food_shop_index"

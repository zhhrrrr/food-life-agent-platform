from fastapi.testclient import TestClient

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.core.config import get_settings
from app.graph.food_graph import run_food_agent
from app.graph.router import IntentResult, route_intent
from app.llm.chat_model import ZHIPU_OPENAI_COMPATIBLE_BASE_URL, resolve_chat_model_options
from app.main import app
from app.rag.context_builder import build_context_summary, build_references
from app.rag.retriever import RagRetriever, RetrievalResult, retrieve_for_agent
from app.search.schemas import SearchHit


client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert "X-Request-Id" in response.headers


def test_settings_version() -> None:
    settings = get_settings()

    assert settings.app_version == "0.9.1"
    assert settings.es_enabled is False
    assert settings.es_index_shop == "food_shop_index"
    assert settings.llm_router_enabled is False


def test_chat_routes_to_recommend_agent() -> None:
    message = "\u5e2e\u6211\u63a8\u8350\u4e00\u5bb6\u5ddd\u83dc\u9986"
    response = client.post("/api/agent/chat", json={"message": message})

    assert response.status_code == 200
    data = response.json()
    assert data["intent"] == "recommend"
    assert data["agent"] == "recommend"
    assert "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u53ef\u7528\u4f9d\u636e" in data["response"]
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
    assert "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u76f8\u5173\u5e73\u53f0\u89c4\u5219" in data["response"]
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

    assert context == "1. Shu Xiang [food_shop_index]: Sichuan restaurant for team dinners."
    assert references[0].id == "shop-1"
    assert references[0].source == "food_shop_index"


class FakeSearchService:
    def __init__(self) -> None:
        self.calls: list[tuple[list[str], str, int]] = []

    def search_many(self, indices: list[str], query: str, size: int = 3) -> list[SearchHit]:
        self.calls.append((indices, query, size))
        return [
            SearchHit(
                id="shop-1",
                title="Shu Xiang",
                content="Sichuan restaurant for team dinners.",
                source="food_shop_index",
                score=4.2,
            )
        ]


def test_rag_retriever_searches_recommend_indices() -> None:
    settings = get_settings()
    search_service = FakeSearchService()

    result = RagRetriever(settings=settings, search_service=search_service).retrieve(
        "recommend",
        "\u5ddd\u83dc\u9986",
        size=2,
    )

    assert result.has_hits is True
    assert result.hits[0].id == "shop-1"
    assert search_service.calls == [
        (
            [
                "food_shop_index",
                "food_package_index",
                "food_review_index",
                "food_activity_index",
                "food_coupon_index",
            ],
            "\u5ddd\u83dc\u9986",
            2,
        )
    ]


class FakeRagRetriever:
    def __init__(self, hit: SearchHit) -> None:
        self.hit = hit

    def retrieve(self, agent: str, query: str, size: int = 3) -> RetrievalResult:
        return RetrievalResult(agent=agent, query=query, hits=[self.hit])


def test_recommend_agent_uses_rag_hits(monkeypatch) -> None:
    hit = SearchHit(
        id="shop-1",
        title="Shu Xiang",
        content="Sichuan restaurant for team dinners.",
        source="food_shop_index",
        score=4.2,
    )
    monkeypatch.setattr(
        "app.agents.recommend_agent.RagRetriever",
        lambda: FakeRagRetriever(hit),
    )

    result = recommend_agent_node({"message": "\u63a8\u8350\u5ddd\u83dc"})

    assert "\u6839\u636e\u68c0\u7d22\u7ed3\u679c" in str(result["response"])
    assert result["references"] == [
        {
            "id": "shop-1",
            "title": "Shu Xiang",
            "source": "food_shop_index",
            "score": 4.2,
        }
    ]
    assert result["rag_mode"] == "retrieval_template"


def test_support_agent_uses_rag_hits(monkeypatch) -> None:
    hit = SearchHit(
        id="rule-1",
        title="Refund Rule",
        content="Refunds are reviewed after payment records are verified.",
        source="food_rule_index",
        score=5.0,
    )
    monkeypatch.setattr(
        "app.agents.support_agent.RagRetriever",
        lambda: FakeRagRetriever(hit),
    )

    result = support_agent_node({"message": "\u9000\u6b3e\u89c4\u5219"})

    assert "\u5e73\u53f0\u89c4\u5219\u68c0\u7d22\u7ed3\u679c" in str(result["response"])
    assert result["references"] == [
        {
            "id": "rule-1",
            "title": "Refund Rule",
            "source": "food_rule_index",
            "score": 5.0,
        }
    ]
    assert result["rag_mode"] == "retrieval_template"


def test_food_graph_records_router_metadata() -> None:
    result = run_food_agent("\u9000\u6b3e\u89c4\u5219\u662f\u4ec0\u4e48")

    assert result["intent"] == "support"
    assert result["agent"] == "support"
    assert result["router_confidence"] == 0.65
    assert "fallback rule" in result["router_reason"]


class FakeStructuredModel:
    def __init__(self, result: IntentResult) -> None:
        self.result = result
        self.messages: list[tuple[str, str]] | None = None

    def invoke(self, messages: list[tuple[str, str]]) -> IntentResult:
        self.messages = messages
        return self.result


class FakeChatModel:
    def __init__(self, result: IntentResult) -> None:
        self.result = result
        self.schema: object | None = None
        self.structured_model: FakeStructuredModel | None = None

    def with_structured_output(self, schema: object) -> FakeStructuredModel:
        self.schema = schema
        self.structured_model = FakeStructuredModel(self.result)
        return self.structured_model


def test_route_intent_uses_llm_structured_output_when_enabled() -> None:
    llm_result = IntentResult(
        intent="support",
        confidence=0.91,
        reason="The user asks about refund policy.",
    )
    chat_model = FakeChatModel(llm_result)

    result = route_intent(
        "\u8fd9\u4e2a\u9000\u6b3e\u89c4\u5219\u600e\u4e48\u7b97",
        settings=get_settings().model_copy(update={"llm_router_enabled": True}),
        chat_model=chat_model,
    )

    assert result == llm_result
    assert chat_model.schema is IntentResult
    assert chat_model.structured_model is not None
    assert chat_model.structured_model.messages is not None
    assert chat_model.structured_model.messages[-1] == (
        "human",
        "\u8fd9\u4e2a\u9000\u6b3e\u89c4\u5219\u600e\u4e48\u7b97",
    )


def test_route_intent_falls_back_when_llm_fails() -> None:
    class BrokenChatModel:
        def with_structured_output(self, schema: object) -> object:
            raise RuntimeError("llm unavailable")

    result = route_intent(
        "\u67e5\u8be2\u8ba2\u5355",
        settings=get_settings().model_copy(update={"llm_router_enabled": True}),
        chat_model=BrokenChatModel(),
    )

    assert result.intent == "trade"
    assert result.confidence == 0.65
    assert "fallback rule" in result.reason


def test_zhipu_chat_model_options_use_openai_compatible_base_url() -> None:
    settings = get_settings().model_copy(
        update={
            "llm_router_enabled": True,
            "llm_provider": "zhipu",
            "llm_model": "glm-4.5-air",
            "llm_api_key": "test-key",
            "llm_base_url": "",
        }
    )

    options = resolve_chat_model_options(settings)

    assert options["model"] == "glm-4.5-air"
    assert options["api_key"] == "test-key"
    assert options["base_url"] == ZHIPU_OPENAI_COMPATIBLE_BASE_URL

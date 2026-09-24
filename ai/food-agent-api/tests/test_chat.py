from fastapi.testclient import TestClient

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.core.config import get_settings
from app.graph.food_graph import run_food_agent
from app.graph.router import IntentResult, route_intent, route_intent_by_rules
from app.llm.chat_model import ZHIPU_OPENAI_COMPATIBLE_BASE_URL, resolve_chat_model_options
from app.main import app
from app.recommend.chain import (
    RecommendAnswer,
    RecommendChain,
    RecommendChainResult,
    RecommendQueryRewrite,
)
from app.rag.context_builder import build_context_summary, build_references
from app.rag.retriever import RagRetriever, RetrievalResult, retrieve_for_agent
from app.search.schemas import SearchHit
from app.support.chain import SupportAnswer, SupportChain, SupportQueryRewrite
from app.support.order_status_client import (
    JavaOrderStatusClient,
    OrderStatusLookup,
    OrderStatusSnapshot,
)


client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert "X-Request-Id" in response.headers


def test_settings_version() -> None:
    settings = get_settings()

    assert settings.app_version == "0.11.1"
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
    assert "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u53ef\u7528\u4e8e\u63a8\u8350" in data["response"]
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


def test_recommend_agent_uses_chain_result(monkeypatch) -> None:
    chain_result = RecommendChainResult(
        response="\u6839\u636e\u68c0\u7d22\u7ed3\u679c\uff0c\u63a8\u8350 Shu Xiang\u3002",
        rag_mode="recommend_agent_llm_rag",
        query="\u63a8\u8350\u5ddd\u83dc",
        rewritten_query="\u5ddd\u83dc \u5546\u5bb6 \u63a8\u8350",
        capabilities=["shop_recommendation", "cuisine_filter"],
        context=[],
        references=[
            {
                "id": "shop-1",
                "title": "Shu Xiang",
                "source": "food_shop_index",
                "score": 4.2,
            }
        ],
    )

    class FakeRecommendChain:
        def run(self, message: str) -> RecommendChainResult:
            assert message == "\u63a8\u8350\u5ddd\u83dc"
            return chain_result

    monkeypatch.setattr(
        "app.agents.recommend_agent.RecommendChain",
        lambda: FakeRecommendChain(),
    )

    result = recommend_agent_node({"message": "\u63a8\u8350\u5ddd\u83dc"})

    assert "\u63a8\u8350 Shu Xiang" in str(result["response"])
    assert result["rag_mode"] == "recommend_agent_llm_rag"
    assert result["rewritten_query"] == "\u5ddd\u83dc \u5546\u5bb6 \u63a8\u8350"
    assert result["references"] == chain_result.references


def test_recommend_chain_uses_rag_hits_with_template_fallback() -> None:
    hit = SearchHit(
        id="shop-1",
        title="Shu Xiang",
        content="Sichuan restaurant for team dinners.",
        source="food_shop_index",
        score=4.2,
    )

    result = RecommendChain(
        settings=get_settings().model_copy(update={"llm_router_enabled": False}),
        retriever=FakeRagRetriever(hit),
    ).run("\u63a8\u8350\u5ddd\u83dc")

    assert "\u6839\u636e\u68c0\u7d22\u7ed3\u679c" in result.response
    assert result.rag_mode == "recommend_agent_llm_rag"
    assert result.rewritten_query == "\u63a8\u8350\u5ddd\u83dc"
    assert result.capabilities == ["shop_recommendation", "cuisine_filter"]
    assert result.references == [
        {
            "id": "shop-1",
            "title": "Shu Xiang",
            "source": "food_shop_index",
            "score": 4.2,
        }
    ]


def test_recommend_chain_rewrites_retrieves_and_generates_llm_answer() -> None:
    hit = SearchHit(
        id="shop-1",
        title="Shu Xiang",
        content="Sichuan restaurant with good reviews and two-person coupon packages.",
        source="food_shop_index",
        score=4.2,
    )
    rewrite = RecommendQueryRewrite(
        search_query="\u5ddd\u83dc \u53cc\u4eba \u4f18\u60e0\u5238 \u597d\u8bc4 \u5546\u5bb6",
        capabilities=[
            "shop_recommendation",
            "cuisine_filter",
            "scene_recommendation",
            "review_semantic_search",
        ],
        reason="The user asks for a two-person Sichuan recommendation with coupons.",
    )
    answer = RecommendAnswer(
        answer="推荐 Shu Xiang：它匹配川菜、双人场景和优惠券线索，且评价较好。",
        used_reference_ids=["shop-1"],
    )

    class TrackingRetriever:
        def __init__(self) -> None:
            self.query = ""

        def retrieve(self, agent: str, query: str, size: int = 3) -> RetrievalResult:
            assert agent == "recommend"
            assert size == 5
            self.query = query
            return RetrievalResult(agent=agent, query=query, hits=[hit])

    class SchemaAwareStructuredModel:
        def __init__(self, result: object) -> None:
            self.result = result

        def invoke(self, messages: list[tuple[str, str]]) -> object:
            assert messages
            return self.result

    class SchemaAwareChatModel:
        def __init__(self) -> None:
            self.structured_kwargs: list[dict[str, object]] = []

        def with_structured_output(self, schema: object, **kwargs: object) -> object:
            self.structured_kwargs.append(kwargs)
            if schema is RecommendQueryRewrite:
                return SchemaAwareStructuredModel(rewrite)
            if schema is RecommendAnswer:
                return SchemaAwareStructuredModel(answer)
            raise AssertionError("unexpected schema")

    chat_model = SchemaAwareChatModel()
    retriever = TrackingRetriever()

    result = RecommendChain(
        settings=get_settings().model_copy(
            update={
                "llm_router_enabled": True,
                "llm_provider": "zhipu",
                "llm_api_key": "test-key",
            }
        ),
        retriever=retriever,
        chat_model=chat_model,
    ).run("\u4e24\u4e2a\u4eba\u5403\u5ddd\u83dc\uff0c\u6700\u597d\u6709\u4f18\u60e0\u5238\u548c\u597d\u8bc4")

    assert retriever.query == rewrite.search_query
    assert result.response == answer.answer
    assert result.rewritten_query == rewrite.search_query
    assert result.capabilities == list(rewrite.capabilities)
    assert result.references[0]["id"] == "shop-1"
    assert chat_model.structured_kwargs == [{"method": "json_mode"}, {"method": "json_mode"}]


def test_recommend_rewrite_rejects_empty_capabilities_and_uses_fallback() -> None:
    class InvalidStructuredModel:
        def invoke(self, messages: list[tuple[str, str]]) -> dict[str, object]:
            return {
                "search_query": "\u9884\u7b97200\u5143 \u7ea6\u4f1a",
                "capabilities": [],
                "reason": "invalid empty capabilities",
            }

    class InvalidChatModel:
        def with_structured_output(self, schema: object, **kwargs: object) -> InvalidStructuredModel:
            assert schema is RecommendQueryRewrite
            assert kwargs == {"method": "json_mode"}
            return InvalidStructuredModel()

    result = RecommendChain(
        settings=get_settings().model_copy(
            update={"llm_router_enabled": True, "llm_provider": "zhipu", "llm_api_key": "test-key"}
        ),
        chat_model=InvalidChatModel(),
    ).rewrite_query("\u9884\u7b97200\u5143\uff0c\u9002\u5408\u7ea6\u4f1a")

    assert result.search_query == "\u9884\u7b97200\u5143\uff0c\u9002\u5408\u7ea6\u4f1a"
    assert result.capabilities == ["budget_filter", "scene_recommendation"]


def test_support_agent_uses_rag_hits(monkeypatch) -> None:
    hit = SearchHit(
        id="rule-1",
        title="Refund Rule",
        content="Refunds are reviewed after payment records are verified.",
        source="food_rule_index",
        score=5.0,
    )
    class FakeSupportChain:
        def run(self, message: str, authorization: str = "") -> object:
            assert message == "\u9000\u6b3e\u89c4\u5219"
            assert authorization == ""
            return SupportChain(
                settings=get_settings().model_copy(update={"llm_router_enabled": False}),
                retriever=FakeRagRetriever(hit),
            ).run(message)

    monkeypatch.setattr("app.agents.support_agent.SupportChain", lambda: FakeSupportChain())

    result = support_agent_node({"message": "\u9000\u6b3e\u89c4\u5219"})

    assert "\u5e73\u53f0\u89c4\u5219\u548c\u5f53\u524d\u67e5\u8be2\u7ed3\u679c" in str(result["response"])
    assert result["references"] == [
        {
            "id": "rule-1",
            "title": "Refund Rule",
            "source": "food_rule_index",
            "score": 5.0,
        }
    ]
    assert result["rag_mode"] == "support_agent_llm_rag"


def test_support_chain_queries_java_order_status_only_for_explicit_order_status() -> None:
    snapshot = OrderStatusSnapshot(
        order_id=12345,
        order_no="NO-12345",
        trade_type="NORMAL",
        order_status="PAID",
    )

    class TrackingOrderStatusClient:
        def __init__(self) -> None:
            self.calls: list[tuple[int, str]] = []

        def get_order_status(self, order_id: int, authorization: str) -> OrderStatusLookup:
            self.calls.append((order_id, authorization))
            return OrderStatusLookup(status="success", order_id=order_id, snapshot=snapshot)

    order_client = TrackingOrderStatusClient()
    result = SupportChain(
        settings=get_settings().model_copy(update={"llm_router_enabled": False}),
        retriever=FakeRagRetriever(
            SearchHit(
                id="rule-1",
                title="Order Status Rule",
                content="A paid order can be used according to its use rule.",
                source="food_rule_index",
                score=5.0,
            )
        ),
        order_status_client=order_client,  # type: ignore[arg-type]
    ).run("\u8ba2\u5355 12345 \u7684\u8ba2\u5355\u72b6\u6001\u662f\u4ec0\u4e48", authorization="token-1")

    assert order_client.calls == [(12345, "token-1")]
    assert result.capabilities == ["order_status_explanation"]
    assert result.order_status == snapshot.model_dump()
    assert result.references[-1]["source"] == "java_trade_order_query"
    assert "order_status=PAID" in result.response


def test_support_chain_does_not_call_java_without_an_explicit_order_id() -> None:
    class FailingOrderStatusClient:
        def get_order_status(self, order_id: int, authorization: str) -> OrderStatusLookup:
            raise AssertionError("Java must not be called without an explicit order ID")

    result = SupportChain(
        settings=get_settings().model_copy(update={"llm_router_enabled": False}),
        retriever=FakeRagRetriever(
            SearchHit(
                id="rule-1",
                title="Order Status Rule",
                content="Order status is determined by current order data.",
                source="food_rule_index",
                score=5.0,
            )
        ),
        order_status_client=FailingOrderStatusClient(),  # type: ignore[arg-type]
    ).run("\u8ba2\u5355\u72b6\u6001\u600e\u4e48\u89e3\u91ca", authorization="token-1")

    assert "\u8bf7\u63d0\u4f9b\u8ba2\u5355 ID" in result.response


def test_support_chain_uses_llm_answer_with_rule_and_order_evidence() -> None:
    snapshot = OrderStatusSnapshot(
        order_id=12345,
        order_no="NO-12345",
        trade_type="NORMAL",
        order_status="PAID",
    )
    answer = SupportAnswer(
        answer=(
            "\u8ba2\u5355\u5f53\u524d\u4e3a\u5df2\u652f\u4ed8\uff0c"
            "\u4f7f\u7528\u4e0e\u9000\u6b3e\u8bf7\u4ee5\u5e73\u53f0\u89c4\u5219\u4e3a\u51c6\u3002"
        ),
        used_reference_ids=["rule-1", "order:12345"],
    )
    rewrite = SupportQueryRewrite(
        search_query="\u9000\u6b3e\u89c4\u5219 \u8ba2\u5355\u72b6\u6001",
        capabilities=["refund_rule"],
        reason="The user asks about a refund rule and a current order status.",
    )

    class StructuredModel:
        def __init__(self, result: object) -> None:
            self.result = result

        def invoke(self, messages: list[tuple[str, str]]) -> object:
            if self.result is answer:
                assert "order_status=PAID" in messages[-1][1]
            return self.result

    class ChatModel:
        def with_structured_output(self, schema: object, **kwargs: object) -> StructuredModel:
            assert kwargs == {"method": "json_mode"}
            if schema is SupportQueryRewrite:
                return StructuredModel(rewrite)
            if schema is SupportAnswer:
                return StructuredModel(answer)
            raise AssertionError("unexpected schema")

    class SuccessfulOrderStatusClient:
        def get_order_status(self, order_id: int, authorization: str) -> OrderStatusLookup:
            assert authorization == "token-1"
            return OrderStatusLookup(status="success", order_id=order_id, snapshot=snapshot)

    result = SupportChain(
        settings=get_settings().model_copy(
            update={"llm_router_enabled": True, "llm_provider": "zhipu", "llm_api_key": "test-key"}
        ),
        retriever=FakeRagRetriever(
            SearchHit(
                id="rule-1",
                title="Refund Rule",
                content="Refunds follow the platform review rule.",
                source="food_rule_index",
                score=5.0,
            )
        ),
        order_status_client=SuccessfulOrderStatusClient(),  # type: ignore[arg-type]
        chat_model=ChatModel(),
    ).run("\u8ba2\u5355 12345 \u7684\u8ba2\u5355\u72b6\u6001\u548c\u9000\u6b3e\u89c4\u5219", authorization="token-1")

    assert result.response == answer.answer
    assert result.capabilities == ["refund_rule", "order_status_explanation"]
    assert result.rewritten_query == rewrite.search_query


def test_java_order_status_client_forwards_authorization(monkeypatch) -> None:
    class FakeResponse:
        status_code = 200

        @staticmethod
        def json() -> dict[str, object]:
            return {
                "code": "0000",
                "data": {
                    "orderId": 12345,
                    "orderNo": "NO-12345",
                    "tradeType": "NORMAL",
                    "orderStatus": "PAID",
                },
            }

    def fake_get(url: str, headers: dict[str, str], timeout: float) -> FakeResponse:
        assert url == "http://127.0.0.1:8301/api/trade/orders/12345"
        assert headers == {"authorization": "token-1"}
        assert timeout == 5.0
        return FakeResponse()

    monkeypatch.setattr("app.support.order_status_client.httpx.get", fake_get)
    result = JavaOrderStatusClient(settings=get_settings()).get_order_status(12345, "token-1")

    assert result.status == "success"
    assert result.snapshot is not None
    assert result.snapshot.order_status == "PAID"


def test_food_graph_records_router_metadata() -> None:
    result = run_food_agent("\u9000\u6b3e\u89c4\u5219\u662f\u4ec0\u4e48")

    assert result["intent"] == "support"
    assert result["agent"] == "support"
    assert result["router_confidence"] == 0.65
    assert "fallback rule" in result["router_reason"]


def test_rule_router_sends_support_questions_to_support_before_trade() -> None:
    assert route_intent_by_rules("\u62fc\u56e2\u89c4\u5219\u662f\u4ec0\u4e48").intent == "support"
    assert route_intent_by_rules("\u7533\u8bf7\u9000\u6b3e").intent == "trade"


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
        self.structured_kwargs: dict[str, object] = {}
        self.structured_model: FakeStructuredModel | None = None

    def with_structured_output(self, schema: object, **kwargs: object) -> FakeStructuredModel:
        self.schema = schema
        self.structured_kwargs = kwargs
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


def test_route_intent_uses_json_mode_for_zhipu_provider() -> None:
    llm_result = IntentResult(
        intent="recommend",
        confidence=0.95,
        reason="The user asks for food recommendations.",
    )
    chat_model = FakeChatModel(llm_result)

    result = route_intent(
        "\u63a8\u8350\u5ddd\u83dc\u9986",
        settings=get_settings().model_copy(
            update={
                "llm_router_enabled": True,
                "llm_provider": "zhipu",
            }
        ),
        chat_model=chat_model,
    )

    assert result == llm_result
    assert chat_model.structured_kwargs == {"method": "json_mode"}


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

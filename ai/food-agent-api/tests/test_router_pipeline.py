from typing import Any

from fastapi.testclient import TestClient

from app.core.config import Settings
from app.graph.router import ROUTING_TOOLS, route_intent
from app.graph.semantic_router import ROUTE_EXAMPLES, SemanticRouteMatch, SemanticRouter
from app.llm.embedding_model import (
    SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL,
    resolve_embedding_model_options,
)
from app.llm.chat_model import ZHIPU_OPENAI_COMPATIBLE_BASE_URL
from app.main import app
from app.memory.conversation_memory import ConversationTurn, RedisConversationMemory


def test_high_confidence_semantic_route_bypasses_llm() -> None:
    class AcceptedSemanticRouter:
        def match(
            self,
            message: str,
            history: list[ConversationTurn],
        ) -> SemanticRouteMatch:
            assert message == "帮我找一家适合约会的餐厅"
            assert history == []
            return SemanticRouteMatch(
                intent="recommend",
                score=0.93,
                margin=0.18,
                matched_example="想找适合约会而且环境安静的餐厅",
                accepted=True,
            )

    class LlmMustNotRun:
        def bind_tools(self, tools: object, **kwargs: object) -> object:
            raise AssertionError("high-confidence semantic route must bypass LLM")

    result = route_intent(
        "帮我找一家适合约会的餐厅",
        settings=Settings(llm_router_enabled=True),
        semantic_router=AcceptedSemanticRouter(),  # type: ignore[arg-type]
        chat_model=LlmMustNotRun(),
    )

    assert result.intent == "recommend"
    assert result.source == "semantic"
    assert result.tool == "route_recommend"
    assert result.semantic_score == 0.93


def test_zhipu_embedding_options_use_separate_key_and_compatible_url() -> None:
    options = resolve_embedding_model_options(
        Settings(
            semantic_router_enabled=True,
            embedding_provider="zhipu",
            embedding_model="embedding-3",
            embedding_api_key="embedding-test-key",
        )
    )

    assert options["model"] == "embedding-3"
    assert options["api_key"] == "embedding-test-key"
    assert options["base_url"] == ZHIPU_OPENAI_COMPATIBLE_BASE_URL


def test_siliconflow_embedding_options_use_official_compatible_url() -> None:
    options = resolve_embedding_model_options(
        Settings(
            semantic_router_enabled=True,
            embedding_provider="siliconflow",
            embedding_model="BAAI/bge-m3",
            embedding_api_key="embedding-test-key",
        )
    )

    assert options["model"] == "BAAI/bge-m3"
    assert options["base_url"] == SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL


def test_low_confidence_semantic_route_uses_llm_function_calling() -> None:
    history = [
        ConversationTurn(
            user="套餐 12 值得买吗",
            assistant="我可以先帮你查询实时价格和库存。",
            intent="recommend",
        )
    ]

    class RejectedSemanticRouter:
        def match(
            self,
            message: str,
            received_history: list[ConversationTurn],
        ) -> SemanticRouteMatch:
            assert received_history == history
            return SemanticRouteMatch(
                intent="recommend",
                score=0.75,
                margin=0.01,
                matched_example="预算两百元有什么双人套餐推荐",
                accepted=False,
            )

    class ToolResponse:
        tool_calls = [
            {
                "name": "route_trade",
                "args": {
                    "confidence": 0.94,
                    "reason": "The user now asks for current stock.",
                },
            }
        ]

    class BoundModel:
        def __init__(self) -> None:
            self.messages: list[tuple[str, str]] = []

        def invoke(self, messages: list[tuple[str, str]]) -> ToolResponse:
            self.messages = messages
            return ToolResponse()

    class ToolCallingModel:
        def __init__(self) -> None:
            self.bound = BoundModel()
            self.tools: object = None
            self.tool_choice = ""

        def bind_tools(self, tools: object, **kwargs: object) -> BoundModel:
            self.tools = tools
            self.tool_choice = str(kwargs.get("tool_choice"))
            return self.bound

    model = ToolCallingModel()
    result = route_intent(
        "那现在还有库存吗",
        settings=Settings(llm_router_enabled=True, llm_api_key="test-key"),
        semantic_router=RejectedSemanticRouter(),  # type: ignore[arg-type]
        chat_model=model,
        conversation_history=history,
    )

    assert result.intent == "trade"
    assert result.source == "llm_tool"
    assert result.tool == "route_trade"
    assert model.tools == ROUTING_TOOLS
    assert model.tool_choice == "required"
    assert ("human", history[0].user) in model.bound.messages
    assert model.bound.messages[-1] == ("human", "那现在还有库存吗")


def test_router_tool_contract_separates_current_order_query_from_status_meaning() -> None:
    trade_description = ROUTING_TOOLS[1]["function"]["description"]  # type: ignore[index]
    support_description = ROUTING_TOOLS[2]["function"]["description"]  # type: ignore[index]

    assert "concrete order/package/team ID" in trade_description
    assert "Do not use this route" in support_description


class FakeEmbeddingModel:
    def __init__(self, query_vector: list[float]) -> None:
        self.query_vector = query_vector
        self.document_calls = 0
        self.queries: list[str] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        self.document_calls += 1
        assert texts == [example for _, example in ROUTE_EXAMPLES]
        vectors = {
            "recommend": [1.0, 0.0, 0.0],
            "trade": [0.0, 1.0, 0.0],
            "support": [0.0, 0.0, 1.0],
        }
        return [vectors[intent] for intent, _ in ROUTE_EXAMPLES]

    def embed_query(self, text: str) -> list[float]:
        self.queries.append(text)
        return self.query_vector


def test_semantic_router_scores_margin_uses_memory_and_caches_examples() -> None:
    embeddings = FakeEmbeddingModel([0.0, 0.99, 0.1])
    router = SemanticRouter(
        settings=Settings(
            semantic_router_enabled=True,
            semantic_router_high_threshold=0.8,
            semantic_router_min_margin=0.05,
        ),
        embedding_model=embeddings,
    )
    history = [ConversationTurn(user="查询套餐 12 的价格", intent="trade")]

    first = router.match("那库存呢", history)
    second = router.match("还有优惠券吗", history)

    assert first is not None
    assert first.intent == "trade"
    assert first.accepted is True
    assert first.margin > 0.8
    assert second is not None
    assert embeddings.document_calls == 1
    assert "查询套餐 12 的价格" in embeddings.queries[0]
    assert "那库存呢" in embeddings.queries[0]


def test_semantic_router_rejects_ambiguous_margin() -> None:
    router = SemanticRouter(
        settings=Settings(
            semantic_router_enabled=True,
            semantic_router_high_threshold=0.7,
            semantic_router_min_margin=0.05,
        ),
        embedding_model=FakeEmbeddingModel([1.0, 1.0, 0.0]),
    )

    result = router.match("这个怎么样", [])

    assert result is not None
    assert result.score > 0.7
    assert result.margin == 0.0
    assert result.accepted is False


def test_semantic_router_failure_enters_cooldown() -> None:
    class BrokenEmbeddings:
        def __init__(self) -> None:
            self.calls = 0

        def embed_documents(self, texts: list[str]) -> list[list[float]]:
            self.calls += 1
            raise RuntimeError("embedding unavailable")

    embeddings = BrokenEmbeddings()
    router = SemanticRouter(
        settings=Settings(
            semantic_router_enabled=True,
            semantic_router_failure_cooldown_seconds=30,
        ),
        embedding_model=embeddings,
    )

    assert router.match("推荐川菜", []) is None
    assert router.match("查询订单", []) is None
    assert embeddings.calls == 1


class FakeRedisPipeline:
    def __init__(self, client: "FakeRedis") -> None:
        self.client = client
        self.operations: list[tuple[str, tuple[Any, ...]]] = []

    def rpush(self, *args: Any) -> "FakeRedisPipeline":
        self.operations.append(("rpush", args))
        return self

    def ltrim(self, *args: Any) -> "FakeRedisPipeline":
        self.operations.append(("ltrim", args))
        return self

    def expire(self, *args: Any) -> "FakeRedisPipeline":
        self.operations.append(("expire", args))
        return self

    def execute(self) -> None:
        for operation, args in self.operations:
            if operation == "rpush":
                key, value = args
                self.client.values.setdefault(key, []).append(value)
            elif operation == "ltrim":
                key, start, _ = args
                self.client.values[key] = self.client.values.get(key, [])[start:]
            elif operation == "expire":
                key, ttl = args
                self.client.expirations[key] = ttl


class FakeRedis:
    def __init__(self) -> None:
        self.values: dict[str, list[str]] = {}
        self.expirations: dict[str, int] = {}

    def pipeline(self, transaction: bool) -> FakeRedisPipeline:
        assert transaction is True
        return FakeRedisPipeline(self)

    def lrange(self, key: str, start: int, end: int) -> list[str]:
        assert (start, end) == (0, -1)
        return self.values.get(key, [])


def test_redis_conversation_memory_is_bounded_and_expiring() -> None:
    redis_client = FakeRedis()
    memory = RedisConversationMemory(
        settings=Settings(
            conversation_memory_enabled=True,
            conversation_memory_max_turns=2,
            conversation_memory_ttl_seconds=120,
        ),
        redis_client=redis_client,
    )

    for index in range(3):
        memory.append(
            "conversation-1",
            user=f"question-{index}",
            assistant=f"answer-{index}",
            intent="trade",
        )

    turns = memory.load("conversation-1")

    assert [turn.user for turn in turns] == ["question-1", "question-2"]
    assert redis_client.expirations["food-agent-api:conversation:conversation-1"] == 120


def test_redis_memory_failure_skips_follow_up_calls_during_cooldown() -> None:
    class BrokenRedis:
        def __init__(self) -> None:
            self.calls = 0

        def lrange(self, key: str, start: int, end: int) -> list[str]:
            self.calls += 1
            raise RuntimeError("redis unavailable")

    redis_client = BrokenRedis()
    memory = RedisConversationMemory(
        settings=Settings(conversation_memory_enabled=True),
        redis_client=redis_client,
    )

    assert memory.load("conversation-1") == []
    assert memory.load("conversation-1") == []
    assert redis_client.calls == 1


def test_chat_api_loads_and_appends_conversation_memory(monkeypatch) -> None:
    class TrackingMemory:
        def __init__(self) -> None:
            self.appended: list[tuple[str, str, str, str]] = []

        def load(self, conversation_id: str) -> list[ConversationTurn]:
            assert conversation_id == "conversation-1"
            return [ConversationTurn(user="查询订单 12345", intent="trade")]

        def append(
            self,
            conversation_id: str,
            *,
            user: str,
            assistant: str,
            intent: str,
        ) -> None:
            self.appended.append((conversation_id, user, assistant, intent))

    memory = TrackingMemory()
    monkeypatch.setattr("app.api.chat.conversation_memory", memory)

    response = TestClient(app).post(
        "/api/agent/chat",
        json={"message": "那这个呢", "conversation_id": "conversation-1"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["conversation_id"] == "conversation-1"
    assert payload["intent"] == "trade"
    assert payload["router_source"] == "rules"
    assert memory.appended[0][0:2] == ("conversation-1", "那这个呢")
    assert memory.appended[0][3] == "trade"


def test_rule_fallback_does_not_let_old_topic_override_explicit_new_query() -> None:
    result = route_intent(
        "推荐一家川菜馆",
        settings=Settings(llm_router_enabled=False),
        conversation_history=[
            ConversationTurn(user="退款规则是什么", intent="support")
        ],
    )

    assert result.intent == "recommend"
    assert result.source == "rules"

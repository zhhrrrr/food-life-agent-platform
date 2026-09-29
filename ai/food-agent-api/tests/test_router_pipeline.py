from collections import Counter
from typing import Any

from fastapi.testclient import TestClient

from app.core.config import Settings
from app.graph.route_examples import (
    MIN_EXAMPLES_PER_CAPABILITY,
    MIN_EXAMPLES_PER_INTENT,
    ROUTE_EXAMPLES,
    validate_route_examples,
)
from app.graph.semantic_router import SemanticRouteMatch, SemanticRouter
from app.llm.chat_model import ZHIPU_OPENAI_COMPATIBLE_BASE_URL, build_chat_model
from app.llm.embedding_model import (
    SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL,
    build_embedding_model,
    resolve_embedding_model_options,
)
from app.main import app
from app.memory.conversation_memory import ConversationTurn, RedisConversationMemory
from app.orchestration.planner import DynamicPlanner
from evaluation.semantic_route_cases import SEMANTIC_ROUTE_EVALUATION_CASES


def test_semantic_route_corpus_has_production_coverage() -> None:
    validate_route_examples()
    intent_counts = Counter(example.intent for example in ROUTE_EXAMPLES)
    capability_counts = Counter(
        (example.intent, example.capability) for example in ROUTE_EXAMPLES
    )

    assert len(ROUTE_EXAMPLES) == 138
    assert intent_counts == {"recommend": 50, "trade": 49, "support": 39}
    assert all(count >= MIN_EXAMPLES_PER_INTENT for count in intent_counts.values())
    assert all(
        count >= MIN_EXAMPLES_PER_CAPABILITY for count in capability_counts.values()
    )
    assert {example.boundary for example in ROUTE_EXAMPLES if example.boundary} >= {
        "recommend_vs_trade",
        "trade_vs_support",
        "trade_action_vs_support_rule",
    }


def test_semantic_route_evaluation_set_is_balanced_and_independent() -> None:
    evaluation_counts = Counter(case.intent for case in SEMANTIC_ROUTE_EVALUATION_CASES)
    route_texts = {example.text.casefold() for example in ROUTE_EXAMPLES}
    evaluation_texts = [
        case.message.casefold() for case in SEMANTIC_ROUTE_EVALUATION_CASES
    ]

    assert len(SEMANTIC_ROUTE_EVALUATION_CASES) == 60
    assert evaluation_counts == {"recommend": 20, "trade": 20, "support": 20}
    assert len(evaluation_texts) == len(set(evaluation_texts))
    assert route_texts.isdisjoint(evaluation_texts)
    assert sum(bool(case.previous_user) for case in SEMANTIC_ROUTE_EVALUATION_CASES) >= 12


def test_high_confidence_semantic_route_bypasses_llm_planner() -> None:
    class AcceptedSemanticRouter:
        def match(
            self,
            message: str,
            history: list[ConversationTurn],
        ) -> SemanticRouteMatch:
            assert message == "\u5e2e\u6211\u627e\u4e00\u5bb6\u9002\u5408\u7ea6\u4f1a\u7684\u9910\u5385"
            assert history == []
            return SemanticRouteMatch(
                intent="recommend",
                score=0.93,
                margin=0.18,
                matched_example="\u60f3\u627e\u9002\u5408\u7ea6\u4f1a\u800c\u4e14\u73af\u5883\u5b89\u9759\u7684\u9910\u5385",
                accepted=True,
            )

    class LlmMustNotRun:
        def with_structured_output(self, schema: object, **kwargs: object) -> object:
            raise AssertionError("accepted semantic route must bypass the LLM planner")

    plan = DynamicPlanner(
        settings=Settings(llm_router_enabled=True),
        semantic_router=AcceptedSemanticRouter(),  # type: ignore[arg-type]
        chat_model=LlmMustNotRun(),
    ).plan("\u5e2e\u6211\u627e\u4e00\u5bb6\u9002\u5408\u7ea6\u4f1a\u7684\u9910\u5385")

    assert plan.source == "semantic"
    assert plan.confidence == 0.93
    assert plan.tasks[0].agent == "recommend"


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
    assert options["check_embedding_ctx_length"] is False


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
    assert options["check_embedding_ctx_length"] is False


def test_llm_clients_are_reused_for_identical_configuration() -> None:
    chat_settings = Settings(
        llm_router_enabled=True,
        llm_model="cache-test-chat",
        llm_api_key="cache-test-key",
    )
    embedding_settings = Settings(
        embedding_model="cache-test-embedding",
        embedding_api_key="cache-test-key",
    )

    assert build_chat_model(chat_settings) is build_chat_model(chat_settings)
    assert build_embedding_model(embedding_settings) is build_embedding_model(
        embedding_settings
    )


class FakeEmbeddingModel:
    def __init__(self, query_vector: list[float]) -> None:
        self.query_vector = query_vector
        self.document_calls = 0
        self.queries: list[str] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        self.document_calls += 1
        assert texts == [example.text for example in ROUTE_EXAMPLES]
        vectors = {
            "recommend": [1.0, 0.0, 0.0],
            "trade": [0.0, 1.0, 0.0],
            "support": [0.0, 0.0, 1.0],
        }
        return [vectors[example.intent] for example in ROUTE_EXAMPLES]

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
    history = [
        ConversationTurn(
            user="\u67e5\u8be2\u5957\u9910 12 \u7684\u4ef7\u683c",
            intent="trade",
        )
    ]

    first = router.match("\u90a3\u5e93\u5b58\u5462", history)
    second = router.match("\u8fd8\u6709\u4f18\u60e0\u5238\u5417", history)

    assert first is not None
    assert first.intent == "trade"
    assert first.accepted is True
    assert first.margin > 0.8
    assert second is not None
    assert embeddings.document_calls == 1
    assert "\u67e5\u8be2\u5957\u9910 12 \u7684\u4ef7\u683c" in embeddings.queries[0]
    assert "\u90a3\u5e93\u5b58\u5462" in embeddings.queries[0]


def test_semantic_router_rejects_ambiguous_margin() -> None:
    router = SemanticRouter(
        settings=Settings(
            semantic_router_enabled=True,
            semantic_router_high_threshold=0.7,
            semantic_router_min_margin=0.05,
        ),
        embedding_model=FakeEmbeddingModel([1.0, 1.0, 0.0]),
    )

    result = router.match("\u8fd9\u4e2a\u600e\u4e48\u6837", [])

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

    assert router.match("\u63a8\u8350\u5ddd\u83dc", []) is None
    assert router.match("\u67e5\u8be2\u8ba2\u5355", []) is None
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
            return [ConversationTurn(user="\u67e5\u8be2\u8ba2\u5355 12345", intent="trade")]

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
        json={"message": "\u90a3\u8fd9\u4e2a\u5462", "conversation_id": "conversation-1"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["conversation_id"] == "conversation-1"
    assert payload["intent"] == "trade"
    assert payload["router_source"] == "rules"
    assert memory.appended[0][0:2] == ("conversation-1", "\u90a3\u8fd9\u4e2a\u5462")
    assert memory.appended[0][3] == "trade"


def test_rule_fallback_does_not_let_old_topic_override_explicit_new_query() -> None:
    plan = DynamicPlanner(settings=Settings(llm_router_enabled=False)).plan(
        "\u63a8\u8350\u4e00\u5bb6\u5ddd\u83dc\u9986",
        history=[
            ConversationTurn(
                user="\u9000\u6b3e\u89c4\u5219\u662f\u4ec0\u4e48",
                intent="support",
            )
        ],
    )

    assert plan.tasks[0].agent == "recommend"
    assert plan.source == "rules"

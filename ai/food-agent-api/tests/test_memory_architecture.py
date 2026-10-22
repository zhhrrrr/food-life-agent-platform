import json
from typing import Any

from app.core.config import Settings
from app.memory.long_term_memory import (
    LongTermMemoryService,
    MemoryEvent,
    UserMemoryVectorStore,
)
from app.memory.session_memory import (
    SessionContext,
    SessionMemoryService,
    SessionSummaryService,
    SessionTurn,
)
from app.sync.memory_sync_consumer import MemorySyncConsumer
from app.trade.storage import TradeStorage


class DictRedis:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}

    def eval(self, script, count, key, version, ttl, value):
        old = self.values.get(key)
        if old and json.loads(old)['version'] > version:
            return 0
        self.setex(key, ttl, value)
        return 1

    def get(self, key: str) -> str | None:
        return self.values.get(key)

    def setex(self, key: str, ttl: int, value: str) -> None:
        assert ttl == 120
        self.values[key] = value


def test_authenticated_session_writes_mysql_before_refreshing_cache(tmp_path) -> None:
    calls: list[str] = []

    class JavaMemory:
        def append(self, session_id: str, authorization: str, **kwargs: Any) -> SessionContext:
            calls.append("mysql")
            return SessionContext(
                session_id=session_id,
                version=1,
                turns=[SessionTurn(user=kwargs["user"], assistant=kwargs["assistant"], sequence_no=2)],
            )

    redis = DictRedis()
    memory = SessionMemoryService(
        Settings(session_memory_enabled=True, session_memory_ttl_seconds=120, session_summary_enabled=False, orchestration_checkpoint_path=str(tmp_path / "memory.sqlite")),
        redis_client=redis,
        java_client=JavaMemory(),  # type: ignore[arg-type]
    )

    status = memory.append(
        "session-1", user_id="7", authorization="Bearer token",
        user="推荐川菜", assistant="好的", intent="recommend", run_id="run-1",
    )

    assert status == "durable"
    assert calls == ["mysql"]
    assert "food-agent-api:session:7:session-1" in redis.values


def test_summary_uses_unsummarized_turn_threshold() -> None:
    service = SessionSummaryService(Settings(session_memory_max_turns=1, session_summary_trigger_turns=2, session_summary_trigger_chars=9999))
    context = SessionContext(
        session_id="session-1",
        summary_through_seq=2,
        turns=[
            SessionTurn(user="old", sequence_no=2),
            SessionTurn(user="new-1", sequence_no=4),
            SessionTurn(user="new-2", sequence_no=6),
            SessionTurn(user="recent", sequence_no=8),
        ],
    )
    assert service.should_summarize(context) is True


def test_long_term_memory_policy_excludes_trade_and_limits_support() -> None:
    class JavaMemory:
        def load_profile(self, authorization: str) -> list[dict[str, str]]:
            return [{"memoryKey": "favorite", "valueJson": '{"value":"川菜"}'}]

    class Vectors:
        def search(self, user_id: int, vector: list[float], **kwargs) -> list[MemoryEvent]:
            return [MemoryEvent(
                event_id="e1", user_id=user_id, event_type="explicit_preference",
                content="用户上次要求记住喜欢川菜", occurred_at=1, expires_at=9999999999,
            )]

    class Embeddings:
        def embed_query(self, text: str) -> list[float]:
            return [1.0]

    service = LongTermMemoryService(
        Settings(long_term_memory_enabled=True),
        java_client=JavaMemory(),  # type: ignore[arg-type]
        vector_store=Vectors(),  # type: ignore[arg-type]
        embeddings=Embeddings(),  # type: ignore[arg-type]
    )

    assert service.context_for(["trade"], "查库存", "7", "token") == {}
    assert service.context_for(["support"], "退款规则", "7", "token") == {}
    assert "events" in service.context_for(["support"], "\u4e0a\u6b21\u8bf4\u4e86\u4ec0\u4e48", "7", "token")["support"]
    assert service.context_for(["recommend"], "recommend", "7", "token")["recommend"]["profile"]["favorite"] == ["\u5ddd\u83dc"]



def test_vector_search_isolated_by_user_and_expiry() -> None:
    class Milvus:
        loaded = False

        def has_collection(self, collection_name: str) -> bool:
            return True

        def get_load_state(self, **kwargs):
            return {"state": "Loaded" if self.loaded else "NotLoad"}

        def load_collection(self, **kwargs):
            self.loaded = True

        def search(self, **kwargs: Any) -> list[list[dict[str, Any]]]:
            assert self.loaded, "collection was released after server restart"
            expression = kwargs["filter"]
            assert "user_id == 7" in expression
            assert 'status == "ACTIVE"' in expression
            assert "expires_at >" in expression
            return [[{"distance": 0.91, "entity": {
                "event_id": "e1", "user_id": 7, "event_type": "preference",
                "content": "喜欢川菜", "entity_type": "", "entity_id": "",
                "source_turn_id": "", "status": "ACTIVE", "occurred_at": 1,
                "expires_at": 9999999999,
            }}]]

    store = UserMemoryVectorStore(
        Settings(long_term_memory_min_score=0.5), client=Milvus()
    )
    assert store.search(7, [1.0])[0].event_id == "e1"


def test_memory_consumer_upsert_and_delete_are_idempotent_commands(tmp_path) -> None:
    class Store:
        def __init__(self) -> None:
            self.upserts: list[str] = []
            self.deletes: list[str] = []
            self.rows = {}

        def upsert(self, event: MemoryEvent, vector: list[float]) -> None:
            self.upserts.append(event.event_id)
            self.rows[event.event_id] = {**event.model_dump(exclude={"score", "version"}), "embedding": vector}

        def delete(self, event_id: str) -> None:
            self.deletes.append(event_id)
            self.rows.pop(event_id, None)

        def get(self, event_id):
            return self.rows.get(event_id)

    class Embeddings:
        def embed_query(self, text: str) -> list[float]:
            return [1.0]

    store = Store()
    class Java:
        def __init__(self):
            self.data = {}
        def event(self, event_id):
            return self.data
    java = Java()
    consumer = MemorySyncConsumer(store=store, embeddings=Embeddings(), java_client=java,
                                  storage=TradeStorage(str(tmp_path / "index.sqlite")))
    message = {
        "eventType": "UPSERT", "eventId": "e1",
        "payload": {
            "userId": 7, "eventType": "preference", "content": "喜欢川菜",
            "status": "ACTIVE", "occurredAt": "2026-01-01T00:00:00",
            "expiresAt": "2099-07-01T00:00:00",
        },
    }
    java.data = {**message["payload"], "eventId": "e1"}
    consumer.handle(message)
    consumer.handle(message)
    java.data = {**java.data, "status": "DELETED"}
    consumer.handle({"eventType": "DELETE", "eventId": "e1", "payload": {"userId": 7}})
    consumer.handle(message)  # A delayed old UPSERT must still observe the tombstone.

    assert store.upserts == ["e1"]
    assert store.deletes == ["e1", "e1"]

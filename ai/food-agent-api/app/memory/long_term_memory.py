from __future__ import annotations

import json
import logging
from collections.abc import Iterable
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.memory.session_memory import AgentMemoryClient
from app.rag.embedding import RagEmbeddingService
from app.vector.milvus_client import ensure_collection_loaded, get_milvus_client

logger = logging.getLogger("food_agent.memory.long_term")
HISTORY_MARKERS = ("之前", "上次", "以前", "历史", "我说过", "还记得")


class MemoryEvent(BaseModel):
    event_id: str
    user_id: int
    event_type: str
    content: str
    entity_type: str = ""
    entity_id: str = ""
    source_turn_id: str = ""
    status: str = "ACTIVE"
    occurred_at: int
    expires_at: int
    score: float = 0.0
    version: int = 0


class UserMemoryVectorStore:
    def __init__(self, settings: Settings | None = None, client: Any | None = None) -> None:
        self.settings = settings or get_settings()
        self._client = client

    def ensure_collection(self) -> None:
        from pymilvus import DataType, MilvusClient

        client = self._required_client()
        name = self.settings.long_term_memory_collection
        if client.has_collection(collection_name=name):
            return
        schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("event_id", DataType.VARCHAR, is_primary=True, max_length=64)
        schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=self.settings.rag_embedding_dimensions)
        schema.add_field("user_id", DataType.INT64)
        schema.add_field("event_type", DataType.VARCHAR, max_length=32)
        schema.add_field("content", DataType.VARCHAR, max_length=1000)
        schema.add_field("entity_type", DataType.VARCHAR, max_length=32)
        schema.add_field("entity_id", DataType.VARCHAR, max_length=64)
        schema.add_field("source_turn_id", DataType.VARCHAR, max_length=64)
        schema.add_field("status", DataType.VARCHAR, max_length=16)
        schema.add_field("occurred_at", DataType.INT64)
        schema.add_field("expires_at", DataType.INT64)
        indexes = MilvusClient.prepare_index_params()
        indexes.add_index(
            field_name="embedding",
            index_type=self.settings.milvus_index_type,
            metric_type=self.settings.milvus_metric_type,
        )
        client.create_collection(
            collection_name=name,
            schema=schema,
            index_params=indexes,
            consistency_level=self.settings.milvus_consistency_level,
        )

    def upsert(self, event: MemoryEvent, vector: list[float]) -> None:
        self.ensure_collection()
        row = event.model_dump(exclude={"score", "version"})
        row["embedding"] = vector
        self._required_client().upsert(
            collection_name=self.settings.long_term_memory_collection,
            data=[row],
            timeout=self.settings.milvus_timeout_seconds,
        )

    def delete(self, event_id: str) -> None:
        client = self._required_client()
        if not client.has_collection(collection_name=self.settings.long_term_memory_collection):
            return
        client.delete(
            collection_name=self.settings.long_term_memory_collection,
            ids=[event_id],
            timeout=self.settings.milvus_timeout_seconds,
        )

    def search(self, user_id: int, vector: list[float], *, limit: int | None = None) -> list[MemoryEvent]:
        client = self._required_client()
        name = self.settings.long_term_memory_collection
        if not client.has_collection(collection_name=name):
            return []
        ensure_collection_loaded(client, name, self.settings.milvus_timeout_seconds)
        now = int(datetime.now(UTC).timestamp())
        result = client.search(
            collection_name=name,
            data=[vector],
            anns_field="embedding",
            filter=f'user_id == {user_id} and status == "ACTIVE" and expires_at > {now}',
            limit=limit or self.settings.long_term_memory_candidate_k,
            output_fields=["event_id", "user_id", "event_type", "content", "entity_type", "entity_id", "source_turn_id", "status", "occurred_at", "expires_at"],
            search_params={"metric_type": self.settings.milvus_metric_type},
            consistency_level=self.settings.milvus_consistency_level,
            timeout=self.settings.milvus_timeout_seconds,
        )
        events = []
        for raw in result[0] if result else []:
            score = float(raw.get("distance") or raw.get("score") or 0)
            if score < self.settings.long_term_memory_min_score:
                continue
            entity = dict(raw.get("entity") or {})
            events.append(MemoryEvent(**entity, score=score))
        return events[: limit if limit is not None else self.settings.long_term_memory_top_k]

    def get(self, event_id: str) -> dict | None:
        client = self._required_client()
        if not client.has_collection(collection_name=self.settings.long_term_memory_collection):
            return None
        ensure_collection_loaded(client, self.settings.long_term_memory_collection, self.settings.milvus_timeout_seconds)
        rows = client.get(collection_name=self.settings.long_term_memory_collection, ids=[event_id],
                          output_fields=["content", "user_id", "status", "expires_at", "occurred_at", "entity_id", "entity_type", "source_turn_id", "event_type", "embedding"],
                          consistency_level="Strong", timeout=self.settings.milvus_timeout_seconds)
        return rows[0] if rows else None

    def _required_client(self) -> Any:
        client = self._client or get_milvus_client(self.settings)
        if client is None:
            raise RuntimeError("Milvus client is unavailable")
        return client


class LongTermMemoryService:
    """Deterministic memory policy: no extra routing LLM and no Trade personalization."""

    def __init__(
        self,
        settings: Settings | None = None,
        *,
        java_client: AgentMemoryClient | None = None,
        vector_store: UserMemoryVectorStore | None = None,
        embeddings: RagEmbeddingService | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.java_client = java_client or AgentMemoryClient(self.settings)
        self.vector_store = vector_store or UserMemoryVectorStore(self.settings)
        self.embeddings = embeddings or RagEmbeddingService(self.settings)

    def context_for(self, agents: Iterable[str], message: str, user_id: str, authorization: str) -> dict[str, dict]:
        if not self.settings.long_term_memory_enabled or not user_id or not authorization:
            return {}
        selected = set(agents)
        result: dict[str, dict] = {}
        if "recommend" in selected:
            result["recommend"] = self._recommend_context(message, int(user_id), authorization)
        if "support" in selected and any(marker in message for marker in HISTORY_MARKERS):
            result["support"] = {"events": self._event_context(message, int(user_id), authorization, include_preferences=True)}
        return {agent: value for agent, value in result.items() if value}

    def remember_explicit(self, message: str, user_id: str, authorization: str, turn_id: str) -> None:
        if not self.settings.long_term_memory_enabled or not user_id or not authorization:
            return
        from app.memory.preferences import preference_updates
        for update in preference_updates(message, settings=self.settings):
            if update.operation == "delete":
                self.java_client.delete_profile(authorization, "food_preference", update.key)
                continue
            self.java_client.upsert_profile(
                authorization,
                memory_type="food_preference",
                memory_key=update.key,
                value_json=update.model_dump_json(exclude={"key", "evidence"}, exclude_none=True),
                source_turn_id=turn_id,
            )
        # Java updates the profile, canonical event and Outbox within one transaction.

    def _recommend_context(self, message: str, user_id: int, authorization: str) -> dict:
        profile = {}
        try:
            for item in self.java_client.load_profile(authorization):
                key = item.get("memoryKey")
                if key not in {"favorite", "avoid", "allergy", "budget"}:
                    continue
                value = json.loads(item["valueJson"])
                if key == "budget":
                    if "max_yuan" in value and "budget_type" in value:
                        profile[key] = {k: value[k] for k in ("max_yuan", "budget_type")}
                else:
                    profile[key] = value.get("values", [value["value"]] if value.get("value") else [])
        except Exception as exc:  # noqa: BLE001
            logger.warning("long_term_profile_load_failed error=%s", type(exc).__name__)
        return {"profile": profile, "events": self._event_context(message, user_id, authorization)}

    def _event_context(self, message: str, user_id: int, authorization: str, *, include_preferences: bool = False) -> list[dict]:
        from app.rag.fusion import reciprocal_rank_fusion
        from app.rag.reranker import RagReranker
        from app.search.schemas import SearchHit
        vector_ids = []
        try:
            vector_ids = [e.event_id for e in self.vector_store.search(
                user_id, self.embeddings.embed_query(message), limit=self.settings.long_term_memory_candidate_k)]
        except Exception as exc:  # noqa: BLE001
            logger.warning("long_term_vector_search_failed error=%s", type(exc).__name__)
        try:
            # Rehydrate vectors from MySQL: deleted/expired/superseded rows cannot leak back into a prompt.
            current = self.java_client.events(authorization, ids=vector_ids) if vector_ids else []
            lexical = self.java_client.events(authorization, query=message)
            hits = lambda rows: [SearchHit(id=r["eventId"], title=r["eventType"], content=r["content"],
                                          source="user_memory", metadata=r) for r in rows if include_preferences or r.get("entityType") != "preference"]
            by_id = {r["eventId"]: r for r in current}
            vector_rows = [by_id[i] for i in vector_ids if i in by_id]
            fused = reciprocal_rank_fusion([("vector", hits(vector_rows)), ("lexical", hits(lexical))])
            ranked = RagReranker(self.settings).rerank(message, fused, size=self.settings.long_term_memory_top_k).hits
            budget = self.settings.memory_context_max_chars // 2
            result = []
            for hit in ranked:
                item = {"event_id": hit.id, "content": hit.content, "occurred_at": hit.metadata.get("occurredAt"),
                        "source_turn_id": hit.metadata.get("sourceTurnId"), "historical": True}
                budget -= len(json.dumps(item, ensure_ascii=False))
                if budget < 0:
                    break
                result.append(item)
            return result
        except Exception as exc:  # noqa: BLE001 - recall must degrade without blocking business execution
            logger.warning("long_term_event_recall_failed error=%s", type(exc).__name__)
            return []


def utc_epoch(value: Any) -> int:
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, list) and len(value) >= 6:
        return int(datetime(*value[:6], tzinfo=UTC).timestamp())
    parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    return int((parsed if parsed.tzinfo else parsed.replace(tzinfo=UTC)).timestamp())


def event_from_java(data: dict) -> MemoryEvent:
    return MemoryEvent(event_id=data["eventId"], user_id=int(data["userId"]), event_type=data["eventType"],
                       content=data["content"], entity_type=data.get("entityType") or "", entity_id=data.get("entityId") or "",
                       source_turn_id=data.get("sourceTurnId") or "", status=data.get("status") or "ACTIVE",
                       occurred_at=utc_epoch(data["occurredAt"]), expires_at=utc_epoch(data["expiresAt"]), version=int(data.get("version") or 0))

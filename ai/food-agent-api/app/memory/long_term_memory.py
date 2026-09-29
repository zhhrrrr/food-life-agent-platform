from __future__ import annotations

import json
import logging
import re
from collections.abc import Iterable
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.memory.session_memory import AgentMemoryClient
from app.rag.embedding import RagEmbeddingService
from app.vector.milvus_client import get_milvus_client

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
        row = event.model_dump(exclude={"score"})
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
        return events[: self.settings.long_term_memory_top_k]

    def duplicate_id(
        self,
        user_id: int,
        event_type: str,
        entity_id: str,
        vector: list[float],
    ) -> str | None:
        for event in self.search(user_id, vector, limit=self.settings.long_term_memory_candidate_k):
            if (
                event.score >= 0.92
                and event.event_type == event_type
                and event.entity_id == entity_id
            ):
                return event.event_id
        return None

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

    def context_for(self, agents: Iterable[str], message: str, user_id: str, authorization: str) -> dict[str, str]:
        if not self.settings.long_term_memory_enabled or not user_id or not authorization:
            return {}
        selected = set(agents)
        result: dict[str, str] = {}
        if "recommend" in selected:
            result["recommend"] = self._recommend_context(message, int(user_id), authorization)
        if "support" in selected and any(marker in message for marker in HISTORY_MARKERS):
            result["support"] = self._event_context(message, int(user_id))
        return {agent: value for agent, value in result.items() if value}

    def remember_explicit(self, message: str, user_id: str, authorization: str, turn_id: str) -> None:
        if not self.settings.long_term_memory_enabled or not user_id or not authorization:
            return
        extracted = _explicit_preferences(message)
        for key, value in extracted.items():
            self.java_client.upsert_profile(
                authorization,
                memory_type="food_preference",
                memory_key=key,
                value_json=json.dumps({"value": value}, ensure_ascii=False),
                source_turn_id=turn_id,
            )
        if "记住" in message and extracted:
            now = datetime.now(UTC)
            content = message[:1000]
            event_type = "explicit_preference"
            entity_id = next(iter(extracted))
            event_id = uuid4().hex
            try:
                vector = self.embeddings.embed_query(content)
                event_id = self.vector_store.duplicate_id(
                    int(user_id), event_type, entity_id, vector
                ) or event_id
            except Exception as exc:  # noqa: BLE001 - MySQL remains source of truth
                logger.warning("long_term_dedup_degraded error=%s", type(exc).__name__)
            event = {
                "eventId": event_id,
                "eventType": event_type,
                "content": content,
                "entityType": "preference",
                "entityId": entity_id,
                "sourceTurnId": turn_id,
                "occurredAt": now.isoformat(),
                "expiresAt": (now + timedelta(days=180)).isoformat(),
            }
            self.java_client.save_event(authorization, event)

    def _recommend_context(self, message: str, user_id: int, authorization: str) -> str:
        sections: list[str] = []
        try:
            profile = self.java_client.load_profile(authorization)
            if profile:
                facts = [f"{item.get('memoryKey')}: {item.get('valueJson')}" for item in profile[:20]]
                sections.append("稳定偏好（用户明确提供）：\n" + "\n".join(facts))
        except Exception as exc:  # noqa: BLE001
            logger.warning("long_term_profile_load_failed error=%s", type(exc).__name__)
        event_context = self._event_context(message, user_id)
        if event_context:
            sections.append(event_context)
        return "\n\n".join(sections)

    def _event_context(self, message: str, user_id: int) -> str:
        try:
            events = self.vector_store.search(user_id, self.embeddings.embed_query(message))
        except Exception as exc:  # noqa: BLE001
            logger.warning("long_term_vector_search_failed error=%s", type(exc).__name__)
            return ""
        if not events:
            return ""
        return "相关历史记忆（可能已过时，不可替代实时业务事实）：\n" + "\n".join(
            f"- {event.content}" for event in events
        )


def _explicit_preferences(message: str) -> dict[str, str]:
    patterns = {
        "favorite": r"(?:我喜欢|我爱吃|偏好)([^，。；]{1,30})",
        "avoid": r"(?:我不吃|不要|忌口)([^，。；]{1,30})",
        "allergy": r"(?:我对)([^，。；]{1,20})(?:过敏)",
        "budget": r"(?:预算|人均)(?:是|在|大约|不超过)?\s*([0-9]{1,5}\s*元?)",
    }
    return {
        key: match.group(1).strip()
        for key, pattern in patterns.items()
        if (match := re.search(pattern, message))
    }

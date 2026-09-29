from __future__ import annotations

import json
import logging
import threading
from datetime import UTC, datetime
from typing import Any

from app.core.config import Settings, get_settings
from app.memory.long_term_memory import MemoryEvent, UserMemoryVectorStore
from app.rag.embedding import RagEmbeddingService

logger = logging.getLogger("food_agent.sync.memory")


class MemorySyncConsumer:
    def __init__(
        self,
        settings: Settings | None = None,
        *,
        store: UserMemoryVectorStore | None = None,
        embeddings: RagEmbeddingService | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.store = store or UserMemoryVectorStore(self.settings)
        self.embeddings = embeddings or RagEmbeddingService(self.settings)
        self._thread: threading.Thread | None = None
        self._connection: Any | None = None
        self._stopping = threading.Event()

    def start(self) -> None:
        if not self.settings.memory_sync_consumer_enabled:
            return
        if self._thread and self._thread.is_alive():
            return
        self._stopping.clear()
        self._thread = threading.Thread(target=self._run, name="memory-sync-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stopping.set()
        if self._connection is not None:
            try:
                self._connection.close()
            except Exception:
                logger.exception("memory_sync_connection_close_failed")

    def handle(self, message: dict[str, Any]) -> None:
        event_type = str(message.get("eventType") or "").upper()
        event_id = str(message.get("eventId") or "")
        if not event_id:
            raise ValueError("memory eventId required")
        if event_type in {"DELETE", "EXPIRE"}:
            self.store.delete(event_id)
            return
        if event_type != "UPSERT":
            raise ValueError(f"unsupported memory event type: {event_type}")
        payload = dict(message.get("payload") or {})
        event = MemoryEvent(
            event_id=event_id,
            user_id=int(payload["userId"]),
            event_type=str(payload["eventType"]),
            content=str(payload["content"]),
            entity_type=str(payload.get("entityType") or ""),
            entity_id=str(payload.get("entityId") or ""),
            source_turn_id=str(payload.get("sourceTurnId") or ""),
            status=str(payload.get("status") or "ACTIVE"),
            occurred_at=_epoch(payload.get("occurredAt")),
            expires_at=_epoch(payload.get("expiresAt")),
        )
        self.store.upsert(event, self.embeddings.embed_query(event.content))

    def _run(self) -> None:
        try:
            import pika
        except ImportError:
            logger.error("pika package is not installed, memory sync consumer disabled")
            return
        while not self._stopping.is_set():
            try:
                self._connection = pika.BlockingConnection(
                    pika.URLParameters(self.settings.rabbitmq_url)
                )
                channel = self._connection.channel()
                channel.basic_qos(prefetch_count=10)
                channel.basic_consume(
                    queue=self.settings.memory_sync_queue,
                    on_message_callback=self._consume,
                )
                logger.info(
                    "memory_sync_consumer_started queue=%s",
                    self.settings.memory_sync_queue,
                )
                channel.start_consuming()
            except Exception as exc:
                if not self._stopping.is_set():
                    logger.warning(
                        "memory_sync_connection_retry error=%s",
                        type(exc).__name__,
                    )
            finally:
                self._connection = None
            self._stopping.wait(5)

    def _consume(self, channel: Any, method: Any, properties: Any, body: bytes) -> None:
        try:
            self.handle(json.loads(body.decode("utf-8")))
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            logger.exception("memory_sync_consume_failed message_id=%s", getattr(properties, "message_id", ""))
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def _epoch(value: Any) -> int:
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, list) and len(value) >= 6:
        return int(datetime(*value[:6], tzinfo=UTC).timestamp())
    return int(datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp())


_consumer = MemorySyncConsumer()


def start_memory_sync_consumer(settings: Settings | None = None) -> None:
    global _consumer
    _consumer = MemorySyncConsumer(settings)
    _consumer.start()


def stop_memory_sync_consumer() -> None:
    _consumer.stop()

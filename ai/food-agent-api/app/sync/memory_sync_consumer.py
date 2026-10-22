from __future__ import annotations

import json
import logging
import threading
from datetime import UTC, datetime
from typing import Any

from app.core.config import Settings, get_settings
from app.core.metrics import MEMORY_OPERATIONS
from app.memory.long_term_memory import (
    UserMemoryVectorStore,
    event_from_java,
    utc_epoch,
)
from app.memory.session_memory import AgentMemoryClient
from app.rag.embedding import RagEmbeddingService

logger = logging.getLogger("food_agent.sync.memory")


class MemorySyncConsumer:
    def __init__(
        self,
        settings: Settings | None = None,
        *,
        store: UserMemoryVectorStore | None = None,
        embeddings: RagEmbeddingService | None = None,
        java_client: Any | None = None,
        storage: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.store = store or UserMemoryVectorStore(self.settings)
        self.embeddings = embeddings or RagEmbeddingService(self.settings)
        self._thread: threading.Thread | None = None
        self._connection: Any | None = None
        self._stopping = threading.Event()
        self.java_client = java_client
        self._storage = storage

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
                self._connection.add_callback_threadsafe(self._connection.close)
            except Exception:
                logger.exception("memory_sync_connection_close_failed")
        if self._thread:
            self._thread.join(timeout=35)
        if self._storage is not None:
            self._storage.database.close()

    def handle(self, message: dict[str, Any]) -> None:
        event_type = str(message.get("eventType") or "").upper()
        event_id = str(message.get("eventId") or "")
        if not event_id:
            raise ValueError("memory eventId required")
        if event_type not in {"UPSERT", "DELETE", "EXPIRE"}:
            raise ValueError(f"unsupported memory event type: {event_type}")
        payload = dict(message.get("payload") or {})
        user_id = str(int(payload["userId"]))
        if self._storage is None:
            from app.trade.storage import TradeStorage
            self._storage = TradeStorage(self.settings.runtime_database_url or self.settings.orchestration_checkpoint_path)
        with self._storage.lease("memory-index:" + event_id) as owned:
            # Read current MySQL state under a shared lease, never trust the age/order of an MQ payload.
            data = (self.java_client or AgentMemoryClient(self.settings, user_id=user_id)).event(event_id)
            if not data or data.get("status") != "ACTIVE" or utc_epoch(data["expiresAt"]) <= int(datetime.now(UTC).timestamp()):
                owned()
                self.store.delete(event_id)
                MEMORY_OPERATIONS.labels("index", "deleted").inc()
                return
            event = event_from_java(data)
            if str(event.user_id) != user_id or event.event_id != event_id:
                raise ValueError("memory event owner mismatch")
            old = self.store.get(event_id)
            row = event.model_dump(exclude={"score", "version", "event_id"})
            if old and all(old.get(k) == v for k, v in row.items()):
                MEMORY_OPERATIONS.labels("index", "unchanged").inc()
                return
            vector = old["embedding"] if old and old.get("content") == event.content else self.embeddings.embed_query(event.content)
            owned()
            self.store.upsert(event, vector)
            MEMORY_OPERATIONS.labels("index", "upserted").inc()

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
                channel.confirm_delivery()
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
            except Exception as exc:  # noqa: BLE001 - reconnect the background consumer
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
        except Exception as exc:
            logger.exception("memory_sync_consume_failed message_id=%s", getattr(properties, "message_id", ""))
            headers = dict(getattr(properties, "headers", None) or {})
            retries = int(headers.get("memoryRetry", 0))
            MEMORY_OPERATIONS.labels("index", "retry" if retries < 5 else "failed").inc()
            if retries < 5 and not isinstance(exc, (ValueError, KeyError, TypeError)):
                import pika
                headers["memoryRetry"] = retries + 1
                channel.basic_publish(exchange="", routing_key=self.settings.memory_sync_queue + ".retry", body=body,
                                      mandatory=True, properties=pika.BasicProperties(headers=headers, delivery_mode=2,
                                      content_type="application/json", message_id=getattr(properties, "message_id", None)))
                channel.basic_ack(delivery_tag=method.delivery_tag)
            else:
                channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


_consumer = MemorySyncConsumer()


def start_memory_sync_consumer(settings: Settings | None = None) -> None:
    global _consumer
    _consumer = MemorySyncConsumer(settings)
    _consumer.start()


def stop_memory_sync_consumer() -> None:
    _consumer.stop()

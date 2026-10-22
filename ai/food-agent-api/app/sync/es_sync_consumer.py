"""Consume business/trade events and update ES/Milvus through SearchIndexer."""
import json
import logging
import threading
from typing import Any

from app.core.config import Settings, get_settings
from app.indexing.indexer import SearchIndexer

logger = logging.getLogger("food_agent.sync.es")


class EsSyncConsumer:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self._thread: threading.Thread | None = None
        self._connection: Any | None = None

    def start(self) -> None:
        if not self.settings.es_sync_consumer_enabled:
            return
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run, name="es-sync-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        # Closing the connection ends the blocking start_consuming() call.
        if self._connection is not None:
            try:
                self._connection.close()
            except Exception:
                logger.exception("rabbitmq_connection_close_failed")

    def _run(self) -> None:
        try:
            import pika
        except ImportError:
            logger.warning("pika package is not installed, ES sync consumer disabled")
            return
        self._connection = pika.BlockingConnection(pika.URLParameters(self.settings.rabbitmq_url))
        channel = self._connection.channel()
        channel.basic_qos(prefetch_count=20)
        for queue_name, source in (
            (self.settings.es_sync_business_queue, "business"),
            (self.settings.es_sync_trade_queue, "trade"),
        ):
            # Bind each queue's source; a late-bound loop variable would misroute events.
            channel.basic_consume(
                queue=queue_name,
                on_message_callback=lambda ch, method, props, body, src=source:
                    self._consume(ch, method, props, body, src),
            )
        logger.info("es_sync_consumer_started")
        channel.start_consuming()

    def _consume(self, channel: Any, method: Any, properties: Any, body: bytes, source: str) -> None:
        try:
            self._handle_event(event=json.loads(body.decode("utf-8")), source=source)
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            logger.exception("es_sync_consume_failed source=%s message_id=%s",
                             source, getattr(properties, "message_id", ""))
            # Failed projections go to the configured DLQ; reconciliation can repair them.
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _handle_event(self, event: dict[str, Any], source: str) -> None:
        tag = str(event.get("tag") or "")
        payload = event.get("payload") or {}
        if source == "business":
            self._handle_business_event(tag=tag, payload=payload)
        else:
            self._handle_trade_event(tag=tag, payload=payload)

    def _handle_business_event(self, tag: str, payload: dict[str, Any]) -> None:
        if tag not in {"review.created", "review.hidden"}:
            # ACK legacy stock events without writing trading facts back into search.
            logger.info("skip_unsupported_business_search_event tag=%s", tag)
            return
        indexer = SearchIndexer(settings=self.settings)
        result = indexer.sync_document(
            scope="reviews", entity_id=payload.get("id"),
            action="DELETE" if tag == "review.hidden" else "UPSERT",
        )
        # Both events change shop review statistics as well as the review document.
        self._sync_related_shop(indexer, payload.get("shopId"))
        self._ensure_success(result.failed)

    def _handle_trade_event(self, tag: str, payload: dict[str, Any]) -> None:
        if tag != "search.document.changed":
            logger.info("skip_unsupported_trade_search_event tag=%s", tag)
            return
        entity_type = payload.get("entityType")
        result = SearchIndexer(settings=self.settings).sync_document(
            scope=str(payload.get("scope")), entity_id=payload.get("entityId"),
            entity_type=None if entity_type is None else str(entity_type),
            action=str(payload.get("action") or "UPSERT"),
        )
        self._ensure_success(result.failed)

    def _sync_related_shop(self, indexer: SearchIndexer, shop_id: Any) -> None:
        if shop_id is not None:
            self._ensure_success(indexer.sync_document(scope="shops", entity_id=shop_id).failed)

    @staticmethod
    def _ensure_success(failed: int) -> None:
        if failed > 0:
            raise RuntimeError("ES sync failed")


_consumer: EsSyncConsumer | None = None


def start_es_sync_consumer(settings: Settings | None = None) -> None:
    global _consumer
    _consumer = EsSyncConsumer(settings=settings)
    _consumer.start()


def stop_es_sync_consumer() -> None:
    if _consumer is not None:
        _consumer.stop()

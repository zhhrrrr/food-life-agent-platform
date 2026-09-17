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
        self._stopping = threading.Event()

    def start(self) -> None:
        if not self.settings.es_sync_consumer_enabled:
            return
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run, name="es-sync-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stopping.set()
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

        params = pika.URLParameters(self.settings.rabbitmq_url)
        self._connection = pika.BlockingConnection(params)
        channel = self._connection.channel()
        channel.basic_qos(prefetch_count=20)

        for queue_name, source in (
            (self.settings.es_sync_business_queue, "business"),
            (self.settings.es_sync_trade_queue, "trade"),
        ):
            channel.basic_consume(
                queue=queue_name,
                on_message_callback=lambda ch, method, props, body, src=source: self._consume(
                    ch,
                    method,
                    props,
                    body,
                    src,
                ),
            )

        logger.info("es_sync_consumer_started")
        channel.start_consuming()

    def _consume(self, channel: Any, method: Any, properties: Any, body: bytes, source: str) -> None:
        try:
            event = json.loads(body.decode("utf-8"))
            self._handle_event(event=event, source=source)
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            message_id = getattr(properties, "message_id", "")
            logger.exception("es_sync_consume_failed source=%s message_id=%s", source, message_id)
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _handle_event(self, event: dict[str, Any], source: str) -> None:
        tag = str(event.get("tag") or "")
        payload = event.get("payload") or {}
        if source == "business":
            self._handle_business_event(tag=tag, payload=payload)
            return
        self._handle_trade_event(tag=tag, payload=payload)

    def _handle_business_event(self, tag: str, payload: dict[str, Any]) -> None:
        indexer = SearchIndexer(settings=self.settings)
        if tag.startswith("stock.") or tag == "package.changed":
            package_id = payload.get("packageId")
            result = indexer.sync_document(scope="packages", entity_id=package_id)
            self._sync_shop_from_package(indexer, package_id)
            self._ensure_success(result.failed)
            return

        if tag == "review.created":
            review_id = payload.get("id")
            result = indexer.sync_document(scope="reviews", entity_id=review_id)
            self._sync_related_shop(indexer, payload.get("shopId"))
            self._ensure_success(result.failed)
            return

        if tag == "review.hidden":
            review_id = payload.get("id")
            result = indexer.sync_document(scope="reviews", entity_id=review_id, action="DELETE")
            self._sync_related_shop(indexer, payload.get("shopId"))
            self._ensure_success(result.failed)
            return

        logger.info("skip_unsupported_business_search_event tag=%s", tag)

    def _handle_trade_event(self, tag: str, payload: dict[str, Any]) -> None:
        if tag != "search.document.changed":
            logger.info("skip_unsupported_trade_search_event tag=%s", tag)
            return

        scope = payload.get("scope")
        entity_id = payload.get("entityId")
        entity_type = payload.get("entityType")
        action = str(payload.get("action") or "UPSERT")
        result = SearchIndexer(settings=self.settings).sync_document(
            scope=str(scope),
            entity_id=entity_id,
            entity_type=None if entity_type is None else str(entity_type),
            action=action,
        )
        self._ensure_success(result.failed)

    def _sync_shop_from_package(self, indexer: SearchIndexer, package_id: Any) -> None:
        document = indexer.export_client.fetch_document(scope="packages", entity_id=package_id)
        if document is None:
            return
        self._sync_related_shop(indexer, document.metadata.get("shopId"))

    def _sync_related_shop(self, indexer: SearchIndexer, shop_id: Any) -> None:
        if shop_id is None:
            return
        result = indexer.sync_document(scope="shops", entity_id=shop_id)
        self._ensure_success(result.failed)

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

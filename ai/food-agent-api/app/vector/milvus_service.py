import hashlib
import json
import logging
from collections.abc import Iterable
from datetime import UTC, datetime
from typing import Any

from app.core.config import Settings, get_settings
from app.search.schemas import SearchDocument, SearchFilters, SearchHit
from app.vector.milvus_client import get_milvus_client

logger = logging.getLogger("food_agent.vector.milvus_service")

OUTPUT_FIELDS = ["id", "title", "content", "source", "metadata"]


class MilvusVectorService:
    def __init__(
        self,
        settings: Settings | None = None,
        client: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self._client = client

    def create_rebuild_collection(self, alias: str) -> str:
        from pymilvus import DataType, MilvusClient

        suffix = datetime.now(UTC).strftime("%Y%m%d%H%M%S%f")
        collection = f"{alias}_v{suffix}"
        schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=128)
        schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=self.settings.rag_embedding_dimensions)
        schema.add_field("title", DataType.VARCHAR, max_length=2048)
        schema.add_field("content", DataType.VARCHAR, max_length=65535)
        schema.add_field("source", DataType.VARCHAR, max_length=256)
        schema.add_field("metadata", DataType.JSON)
        schema.add_field("content_hash", DataType.VARCHAR, max_length=64)
        schema.add_field("status", DataType.INT64)
        schema.add_field("valid_start", DataType.INT64)
        schema.add_field("valid_end", DataType.INT64)
        schema.add_field("price", DataType.INT64)

        index_params = MilvusClient.prepare_index_params()
        index_params.add_index(
            field_name="embedding",
            index_type=self.settings.milvus_index_type,
            metric_type=self.settings.milvus_metric_type,
        )
        self._required_client().create_collection(
            collection_name=collection,
            schema=schema,
            index_params=index_params,
            consistency_level=self.settings.milvus_consistency_level,
            timeout=self.settings.milvus_timeout_seconds,
        )
        return collection

    def index_documents(
        self,
        collection: str,
        documents: Iterable[SearchDocument],
        vectors: list[list[float]],
    ) -> tuple[int, int]:
        materialized = list(documents)
        if len(materialized) != len(vectors):
            raise ValueError("embedding count does not match document count")
        if not materialized:
            return 0, 0

        client = self._required_client()
        indexed = 0
        for start in range(0, len(materialized), self.settings.milvus_batch_size):
            batch_documents = materialized[start : start + self.settings.milvus_batch_size]
            batch_vectors = vectors[start : start + self.settings.milvus_batch_size]
            rows = [
                self._row(document, vector)
                for document, vector in zip(batch_documents, batch_vectors, strict=True)
            ]
            result = client.insert(
                collection_name=collection,
                data=rows,
                timeout=self.settings.milvus_timeout_seconds,
            )
            indexed += int(result.get("insert_count", len(rows)))
        if self.settings.milvus_flush_after_write:
            client.flush(collection_name=collection, timeout=self.settings.milvus_timeout_seconds)
        return indexed, len(materialized) - indexed

    def upsert_document(
        self,
        collection: str,
        document: SearchDocument,
        vector: list[float],
    ) -> bool:
        try:
            result = self._required_client().upsert(
                collection_name=collection,
                data=[self._row(document, vector)],
                timeout=self.settings.milvus_timeout_seconds,
            )
            return int(result.get("upsert_count", 1)) == 1
        except Exception:
            logger.exception("milvus_upsert_failed collection=%s id=%s", collection, document.id)
            return False

    def delete_document(self, collection: str, document_id: str) -> bool:
        try:
            self._required_client().delete(
                collection_name=collection,
                ids=[document_id],
                timeout=self.settings.milvus_timeout_seconds,
            )
            return True
        except Exception:
            logger.exception("milvus_delete_failed collection=%s id=%s", collection, document_id)
            return False

    def document_matches(self, collection: str, document: SearchDocument) -> bool:
        try:
            client = self._required_client()
            self._ensure_loaded(client, collection)
            rows = client.query(
                collection_name=collection,
                ids=[document.id],
                output_fields=["id", "content_hash"],
                timeout=self.settings.milvus_timeout_seconds,
            )
            return bool(rows) and rows[0].get("content_hash") == _document_hash(document)
        except Exception:
            logger.exception(
                "milvus_document_check_failed collection=%s id=%s",
                collection,
                document.id,
            )
            return False

    def list_document_ids(self, collection: str, batch_size: int = 1000) -> set[str]:
        client = self._required_client()
        self._ensure_loaded(client, collection)
        iterator = client.query_iterator(
            collection_name=collection,
            batch_size=max(1, min(batch_size, 1000)),
            output_fields=["id"],
            timeout=self.settings.milvus_timeout_seconds,
        )
        ids: set[str] = set()
        try:
            while True:
                rows = iterator.next()
                if not rows:
                    break
                ids.update(str(row["id"]) for row in rows)
        finally:
            iterator.close()
        return ids

    def search_many(
        self,
        collections: Iterable[str],
        query_vector: list[float],
        *,
        size: int,
        filters: SearchFilters | None = None,
    ) -> list[SearchHit]:
        client = self._required_client()
        expression = self._filter_expression(filters or SearchFilters())
        hits: list[SearchHit] = []
        for collection in dict.fromkeys(collections):
            self._ensure_loaded(client, collection)
            result = client.search(
                collection_name=collection,
                data=[query_vector],
                anns_field="embedding",
                filter=expression,
                limit=size,
                output_fields=OUTPUT_FIELDS,
                search_params={"metric_type": self.settings.milvus_metric_type},
                consistency_level=self.settings.milvus_consistency_level,
                timeout=self.settings.milvus_timeout_seconds,
            )
            hits.extend(
                hit
                for hit in (
                    self._to_search_hit(raw) for raw in (result[0] if result else [])
                )
                if hit.score >= self.settings.rag_vector_min_score
            )
        return sorted(hits, key=lambda hit: hit.score, reverse=True)[:size]

    def count_documents(self, collection: str) -> int:
        stats = self._required_client().get_collection_stats(
            collection_name=collection,
            timeout=self.settings.milvus_timeout_seconds,
        )
        return int(stats.get("row_count", 0))

    def promote_collection(self, alias: str, collection: str) -> str | None:
        client = self._required_client()
        previous = self._alias_target(alias)
        client.load_collection(
            collection_name=collection,
            timeout=self.settings.milvus_timeout_seconds,
        )
        if previous:
            client.alter_alias(
                collection_name=collection,
                alias=alias,
                timeout=self.settings.milvus_timeout_seconds,
            )
        else:
            client.create_alias(
                collection_name=collection,
                alias=alias,
                timeout=self.settings.milvus_timeout_seconds,
            )
        return previous

    def rollback_alias(self, alias: str, previous: str | None) -> None:
        client = self._required_client()
        if previous:
            client.alter_alias(
                collection_name=previous,
                alias=alias,
                timeout=self.settings.milvus_timeout_seconds,
            )
        elif self._alias_target(alias):
            client.drop_alias(alias=alias, timeout=self.settings.milvus_timeout_seconds)

    def discard_collection(self, collection: str) -> None:
        self._required_client().drop_collection(
            collection_name=collection,
            timeout=self.settings.milvus_timeout_seconds,
        )

    def cleanup_old_collections(self, alias: str, current: str, keep: int) -> None:
        prefix = f"{alias}_v"
        versions = sorted(
            (
                name
                for name in self._required_client().list_collections()
                if name.startswith(prefix)
            ),
            reverse=True,
        )
        retained = set([current, *[name for name in versions if name != current]][:keep])
        for collection in versions:
            if collection not in retained:
                self.discard_collection(collection)

    def _required_client(self) -> Any:
        if not self.settings.milvus_enabled:
            raise RuntimeError("Milvus is disabled")
        client = self._client or get_milvus_client(self.settings)
        if client is None:
            raise RuntimeError("Milvus client is unavailable")
        return client

    def _alias_target(self, alias: str) -> str | None:
        try:
            description = self._required_client().describe_alias(
                alias=alias,
                timeout=self.settings.milvus_timeout_seconds,
            )
        except Exception:  # noqa: BLE001 - a missing alias is reported by server code.
            return None
        return str(description.get("collection_name") or "") or None

    def _ensure_loaded(self, client: Any, collection: str) -> None:
        state = client.get_load_state(
            collection_name=collection,
            timeout=self.settings.milvus_timeout_seconds,
        ).get("state")
        if getattr(state, "name", state) == "Loaded":
            return
        client.load_collection(
            collection_name=collection,
            timeout=self.settings.milvus_timeout_seconds,
        )

    @staticmethod
    def _row(document: SearchDocument, vector: list[float]) -> dict[str, Any]:
        metadata = document.metadata
        return {
            "id": document.id,
            "embedding": vector,
            "title": document.title,
            "content": document.content,
            "source": document.source,
            "metadata": metadata,
            "content_hash": _document_hash(document),
            "status": _integer(metadata.get("status"), default=-1),
            "valid_start": _epoch_millis(
                metadata.get("validStartTime") or metadata.get("effectiveStartTime")
            ),
            "valid_end": _epoch_millis(
                metadata.get("validEndTime") or metadata.get("effectiveEndTime")
            ),
            "price": _first_integer(
                metadata.get("price"),
                metadata.get("avgPrice"),
                metadata.get("activityPrice"),
                default=-1,
            ),
        }

    @staticmethod
    def _to_search_hit(raw: dict[str, Any]) -> SearchHit:
        entity = dict(raw.get("entity") or {})
        return SearchHit(
            id=str(entity.get("id") or raw.get("id") or ""),
            title=str(entity.get("title") or ""),
            content=str(entity.get("content") or ""),
            source=str(entity.get("source") or ""),
            score=float(raw.get("distance") or raw.get("score") or 0.0),
            metadata=dict(entity.get("metadata") or {}),
        )

    @staticmethod
    def _filter_expression(filters: SearchFilters) -> str:
        clauses: list[str] = []
        if filters.active_only:
            now = int(datetime.now(UTC).timestamp() * 1000)
            clauses.extend(
                [
                    "(status == -1 or status == 1)",
                    f"(valid_start == 0 or valid_start <= {now})",
                    f"(valid_end == 0 or valid_end >= {now})",
                ]
            )
        if filters.max_price_cents is not None:
            clauses.append(f"(price == -1 or price <= {filters.max_price_cents})")
        return " and ".join(clauses)


def _integer(value: Any, *, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _first_integer(*values: Any, default: int) -> int:
    for value in values:
        parsed = _integer(value, default=default)
        if parsed != default:
            return parsed
    return default


def _epoch_millis(value: Any) -> int:
    if not value:
        return 0
    if isinstance(value, datetime):
        parsed = value
    else:
        try:
            parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        except ValueError:
            return 0
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return int(parsed.timestamp() * 1000)


def _document_hash(document: SearchDocument) -> str:
    payload = json.dumps(
        document.model_dump(mode="json"),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()

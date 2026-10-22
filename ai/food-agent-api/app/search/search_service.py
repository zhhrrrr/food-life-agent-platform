import logging
from collections.abc import Iterable
from datetime import UTC, datetime
from typing import Any

from app.core.config import Settings, get_settings
from app.search.es_client import get_es_client
from app.search.index_schema import index_definition
from app.search.schemas import SearchDocument, SearchFilters, SearchHit

try:
    from elasticsearch.helpers import bulk as es_bulk
except ImportError:  # pragma: no cover - dependency is required in production.
    es_bulk = None

logger = logging.getLogger("food_agent.search.service")


class SearchService:

    def __init__(
        self,
        settings: Settings | None = None,
        *, strict: bool = False,
    ) -> None:
        self.settings = settings or get_settings()
        self.strict = strict

    def search_many(
        self,
        indices: Iterable[str],
        query: str,
        size: int = 3,
        original_query: str | None = None,
        filters: SearchFilters | None = None,
    ) -> list[SearchHit]:
        normalized_indices = list(dict.fromkeys(indices))
        if not self.settings.es_enabled or not normalized_indices:
            if self.strict:
                raise RuntimeError("Evaluation requires enabled Elasticsearch and nonempty index scopes")
            return []

        client = get_es_client()
        if client is None:
            if self.strict:
                raise RuntimeError("Elasticsearch client is unavailable")
            return []

        request: dict[str, Any] = {
            "index": ",".join(normalized_indices),
            "size": size,
            "query": self._build_query(query, original_query, filters),
            "ignore_unavailable": not self.strict,
            "allow_no_indices": not self.strict,
            "search_type": "dfs_query_then_fetch",
        }
        if self.settings.es_search_min_score > 0:
            request["min_score"] = self.settings.es_search_min_score

        try:
            result = client.search(**request)
            if self.strict and (result.get("timed_out") or result.get("_shards", {}).get("failed", 0)):
                raise RuntimeError("Elasticsearch returned incomplete search results")
        except Exception:
            if self.strict:
                raise
            logger.exception(
                "es_search_failed indices=%s query_length=%s",
                ",".join(normalized_indices),
                len(query),
            )
            return []

        return [self._to_search_hit(raw_hit) for raw_hit in result["hits"]["hits"]]

    def create_rebuild_index(self, alias: str, scope: str) -> str:
        client = self._required_client()
        suffix = datetime.now(UTC).strftime("%Y%m%d%H%M%S%f")
        physical_index = f"{alias}_v{suffix}"
        definition = index_definition(
            scope=scope,
            shards=self.settings.es_number_of_shards,
            replicas=self.settings.es_number_of_replicas,
        )
        client.indices.create(index=physical_index, **definition)
        return physical_index

    def promote_index(self, alias: str, physical_index: str) -> None:
        client = self._required_client()
        actions: list[dict[str, Any]] = []

        if bool(client.indices.exists_alias(name=alias)):
            current = client.indices.get_alias(name=alias)
            actions.extend(
                {"remove": {"index": index, "alias": alias}}
                for index in current
                if index != physical_index
            )
        elif bool(client.indices.exists(index=alias)):
            actions.append({"remove_index": {"index": alias}})

        actions.append(
            {
                "add": {
                    "index": physical_index,
                    "alias": alias,
                    "is_write_index": True,
                }
            }
        )
        client.indices.update_aliases(actions=actions)

    def discard_index(self, index: str) -> None:
        client = self._required_client()
        client.indices.delete(index=index, ignore_unavailable=True)

    def cleanup_old_indices(self, alias: str, current_index: str, keep: int) -> None:
        client = self._required_client()
        matching = client.indices.get_alias(index=f"{alias}_v*")
        ordered = sorted(matching.keys(), reverse=True)
        retained_order = [current_index]
        retained_order.extend(index for index in ordered if index != current_index)
        retained = set(retained_order[:keep])
        for index in ordered:
            if index not in retained:
                client.indices.delete(index=index)

    def refresh_index(self, index: str) -> None:
        self._required_client().indices.refresh(index=index)

    def count_documents(self, index: str) -> int:
        result = self._required_client().count(index=index)
        return int(result.get("count", 0))

    def index_document(
        self,
        index: str,
        document: SearchDocument,
    ) -> bool:
        if not self.settings.es_enabled:
            return False

        client = get_es_client()
        if client is None:
            return False

        try:
            client.index(
                index=index,
                id=document.id,
                document=document.model_dump(mode="json"),
            )
        except Exception:
            logger.exception(
                "es_index_document_failed index=%s id=%s",
                index,
                document.id,
            )
            return False

        return True

    def index_documents(
        self,
        index: str,
        documents: Iterable[SearchDocument],
    ) -> tuple[int, int]:
        materialized = list(documents)
        if not materialized:
            return 0, 0
        if not self.settings.es_enabled or es_bulk is None:
            return 0, len(materialized)

        client = get_es_client()
        if client is None:
            return 0, len(materialized)

        try:
            actions = (
                {
                    "_op_type": "index",
                    "_index": index,
                    "_id": document.id,
                    "_source": document.model_dump(mode="json"),
                }
                for document in materialized
            )
            indexed, errors = es_bulk(
                client,
                actions,
                chunk_size=self.settings.es_bulk_chunk_size,
                raise_on_error=False,
                raise_on_exception=False,
            )
        except Exception:
            logger.exception("es_bulk_index_failed index=%s", index)
            return 0, len(materialized)
        return int(indexed), len(errors)

    def delete_document(self, index: str, document_id: str) -> bool:
        if not self.settings.es_enabled:
            return False

        client = get_es_client()
        if client is None:
            return False

        try:
            client.delete(index=index, id=document_id, ignore=[404])
        except Exception:
            logger.exception("es_delete_document_failed index=%s id=%s", index, document_id)
            return False

        return True

    def get_document(self, index: str, document_id: str) -> SearchDocument | None:
        if not self.settings.es_enabled:
            return None

        client = get_es_client()
        if client is None:
            return None

        try:
            result = client.get(index=index, id=document_id, ignore=[404])
        except Exception:
            logger.exception("es_get_document_failed index=%s id=%s", index, document_id)
            return None

        if not result or not result.get("found"):
            return None
        source = result.get("_source") or {}
        try:
            return SearchDocument(**source)
        except Exception:
            logger.exception("es_get_document_invalid_source index=%s id=%s", index, document_id)
            return None

    def list_document_ids(self, index: str, size: int = 10000) -> set[str]:
        if not self.settings.es_enabled:
            return set()

        client = get_es_client()
        if client is None:
            return set()

        page_size = max(1, min(size, 1000))
        pit_id = ""
        document_ids: set[str] = set()
        try:
            pit = client.open_point_in_time(index=index, keep_alive="1m")
            pit_id = str(pit.get("id") or "")
            search_after: list[Any] | None = None
            while True:
                request: dict[str, Any] = {
                    "size": page_size,
                    "query": {"match_all": {}},
                    "_source": False,
                    "pit": {"id": pit_id, "keep_alive": "1m"},
                    "sort": [{"_shard_doc": "asc"}],
                }
                if search_after is not None:
                    request["search_after"] = search_after
                result = client.search(**request)
                pit_id = str(result.get("pit_id") or pit_id)
                hits = result.get("hits", {}).get("hits", [])
                document_ids.update(str(hit.get("_id", "")) for hit in hits)
                if len(hits) < page_size:
                    break
                search_after = hits[-1].get("sort")
                if not search_after:
                    break
        except Exception:
            logger.exception("es_list_document_ids_failed index=%s", index)
            return set()
        finally:
            if pit_id:
                try:
                    client.close_point_in_time(id=pit_id)
                except Exception:
                    logger.exception("es_close_pit_failed index=%s", index)
        return document_ids

    @staticmethod
    def _to_search_hit(raw_hit: dict[str, Any]) -> SearchHit:
        source = raw_hit.get("_source", {})
        return SearchHit(
            id=str(source.get("id") or raw_hit.get("_id", "")),
            title=str(source.get("title", "")),
            content=str(source.get("content", "")),
            source=str(source.get("source", "")),
            score=float(raw_hit.get("_score") or 0.0),
            retrieval_scores={"bm25": float(raw_hit.get("_score") or 0.0)},
            metadata=dict(source.get("metadata") or {}),
        )

    def _required_client(self) -> Any:
        if not self.settings.es_enabled:
            raise RuntimeError("Elasticsearch is disabled")
        client = get_es_client()
        if client is None:
            raise RuntimeError("Elasticsearch client is unavailable")
        return client

    @staticmethod
    def _build_query(
        query: str,
        original_query: str | None,
        filters: SearchFilters | None,
    ) -> dict[str, Any]:
        text_queries = [SearchService._text_query(query, boost=1.0)]
        if original_query and original_query.strip() and original_query.strip() != query.strip():
            text_queries.append(SearchService._text_query(original_query, boost=0.7))

        bool_query: dict[str, Any] = {
            "should": text_queries,
            "minimum_should_match": 1,
        }
        filter_clauses = SearchService._filter_clauses(filters or SearchFilters())
        if filter_clauses:
            bool_query["filter"] = filter_clauses
        return {"bool": bool_query}

    @staticmethod
    def _text_query(query: str, boost: float) -> dict[str, Any]:
        return {
            "multi_match": {
                "query": query,
                "fields": ["title^3", "content", "category"],
                "type": "best_fields",
                "minimum_should_match": "30%",
                "boost": boost,
            }
        }

    @staticmethod
    def _filter_clauses(filters: SearchFilters) -> list[dict[str, Any]]:
        clauses: list[dict[str, Any]] = []
        if filters.shop_ids:
            clauses.append({"terms": {"metadata.shopId": filters.shop_ids}})
        if filters.active_only:
            clauses.extend(
                [
                    {
                        "bool": {
                            "should": [
                                {"range": {"metadata.validStartTime": {"lte": "now"}}},
                                {
                                    "bool": {
                                        "must_not": {
                                            "exists": {"field": "metadata.validStartTime"}
                                        }
                                    }
                                },
                            ],
                            "minimum_should_match": 1,
                        }
                    },
                    {
                        "bool": {
                            "should": [
                                {"range": {"metadata.validEndTime": {"gte": "now"}}},
                                {
                                    "bool": {
                                        "must_not": {
                                            "exists": {"field": "metadata.validEndTime"}
                                        }
                                    }
                                },
                            ],
                            "minimum_should_match": 1,
                        }
                    },
                    {
                        "bool": {
                            "should": [
                                {"term": {"metadata.status": 1}},
                                {
                                    "bool": {
                                        "must_not": {"exists": {"field": "metadata.status"}}
                                    }
                                },
                            ],
                            "minimum_should_match": 1,
                        }
                    },
                ]
            )

        specific_price_filters = (
            filters.min_avg_price_cents is not None
            or filters.max_avg_price_cents is not None
            or filters.min_item_price_cents is not None
            or filters.max_item_price_cents is not None
        )
        if specific_price_filters:
            avg_clause = _price_filter_clause(
                ("metadata.avgPrice",),
                filters.min_avg_price_cents,
                filters.max_avg_price_cents,
            )
            item_clause = _price_filter_clause(
                ("metadata.price", "metadata.activityPrice"),
                filters.min_item_price_cents,
                filters.max_item_price_cents,
            )
            clauses.extend(clause for clause in (avg_clause, item_clause) if clause)
        else:
            generic_clause = _price_filter_clause(
                ("metadata.price", "metadata.avgPrice", "metadata.activityPrice"),
                filters.min_price_cents,
                filters.max_price_cents,
            )
            if generic_clause:
                clauses.append(generic_clause)
        return clauses


def _price_filter_clause(
    fields: tuple[str, ...],
    minimum: int | None,
    maximum: int | None,
) -> dict[str, Any] | None:
    if minimum is None and maximum is None:
        return None
    price_range: dict[str, int] = {}
    if minimum is not None:
        price_range["gte"] = minimum
    if maximum is not None:
        price_range["lte"] = maximum
    return {
        "bool": {
            "should": [
                *[{"range": {field: price_range}} for field in fields],
                {
                    "bool": {
                        "must_not": [
                            {"exists": {"field": field}} for field in fields
                        ]
                    }
                },
            ],
            "minimum_should_match": 1,
        }
    }

import logging
from collections.abc import Iterable
from typing import Any

from app.core.config import Settings, get_settings
from app.search.es_client import get_es_client
from app.search.schemas import SearchDocument, SearchHit

logger = logging.getLogger("food_agent.search.service")


class SearchService:

    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    def search_many(
        self,
        indices: Iterable[str],
        query: str,
        size: int = 3,
    ) -> list[SearchHit]:
        hits: list[SearchHit] = []
        for index in indices:
            hits.extend(self.search(index=index, query=query, size=size))
        return sorted(hits, key=lambda hit: hit.score, reverse=True)[:size]

    def search(
        self,
        index: str,
        query: str,
        size: int = 3,
    ) -> list[SearchHit]:
        if not self.settings.es_enabled:
            return []

        client = get_es_client()
        if client is None:
            return []

        try:
            result = client.search(
                index=index,
                size=size,
                query={
                    "multi_match": {
                        "query": query,
                        "fields": [
                            "title^3",
                            "content",
                            "category",
                            "tags",
                        ],
                    }
                },
            )
        except Exception:
            logger.exception("es_search_failed index=%s query=%s", index, query)
            return []

        return [self._to_search_hit(raw_hit) for raw_hit in result["hits"]["hits"]]

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
                document=document.model_dump(),
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
        indexed = 0
        failed = 0
        for document in documents:
            if self.index_document(index=index, document=document):
                indexed += 1
            else:
                failed += 1
        return indexed, failed

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

        try:
            result = client.search(
                index=index,
                size=size,
                query={"match_all": {}},
                _source=False,
            )
        except Exception:
            logger.exception("es_list_document_ids_failed index=%s", index)
            return set()

        return {str(hit.get("_id", "")) for hit in result.get("hits", {}).get("hits", [])}

    @staticmethod
    def _to_search_hit(raw_hit: dict[str, Any]) -> SearchHit:
        source = raw_hit.get("_source", {})
        return SearchHit(
            id=str(source.get("id") or raw_hit.get("_id", "")),
            title=str(source.get("title", "")),
            content=str(source.get("content", "")),
            source=str(source.get("source", "")),
            score=float(raw_hit.get("_score") or 0.0),
            metadata=dict(source.get("metadata") or {}),
        )

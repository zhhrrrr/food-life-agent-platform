import logging
from functools import lru_cache
from typing import Any

from app.core.config import get_settings

logger = logging.getLogger("food_agent.search.es_client")

try:
    from elasticsearch import Elasticsearch
except ImportError:  # pragma: no cover - exercised when dependency is absent.
    Elasticsearch = None  # type: ignore[assignment]


@lru_cache(maxsize=1)
def get_es_client() -> Any | None:
    settings = get_settings()
    if not settings.es_enabled:
        return None

    if Elasticsearch is None:
        logger.warning("Elasticsearch package is not installed")
        return None

    return Elasticsearch(settings.es_url)

import logging
from functools import lru_cache
from time import monotonic
from typing import Any

from app.core.config import Settings, get_settings

logger = logging.getLogger("food_agent.vector.milvus_client")
_FAILURE_COOLDOWN_SECONDS = 10.0
_failure_until: dict[tuple[str, str, str, float], float] = {}


def get_milvus_client(settings: Settings | None = None) -> Any | None:
    resolved = settings or get_settings()
    if not resolved.milvus_enabled:
        return None
    key = (
        resolved.milvus_uri,
        resolved.milvus_token,
        resolved.milvus_database,
        resolved.milvus_timeout_seconds,
    )
    if monotonic() < _failure_until.get(key, 0.0):
        return None
    try:
        client = _connect(*key)
    except Exception:
        _failure_until[key] = monotonic() + _FAILURE_COOLDOWN_SECONDS
        logger.exception("milvus_client_initialization_failed uri=%s", resolved.milvus_uri)
        return None
    _failure_until.pop(key, None)
    return client


@lru_cache(maxsize=4)
def _connect(
    uri: str,
    token: str,
    database: str,
    timeout_seconds: float,
) -> Any | None:
    from pymilvus import MilvusClient

    options: dict[str, Any] = {
        "uri": uri,
        "db_name": database,
        "timeout": timeout_seconds,
    }
    if token:
        options["token"] = token
    return MilvusClient(**options)

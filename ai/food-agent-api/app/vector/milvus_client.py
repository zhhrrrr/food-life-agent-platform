import logging
from functools import lru_cache
from time import monotonic
from typing import Any

from app.core.config import Settings, get_settings

logger = logging.getLogger("food_agent.vector.milvus_client")
_FAILURE_COOLDOWN_SECONDS = 10.0
_failure_until: dict[tuple[str, str, str, float], float] = {}


def ensure_collection_loaded(client: Any, collection: str, timeout: float) -> None:
    state = client.get_load_state(collection_name=collection, timeout=timeout).get("state")
    if getattr(state, "name", state) != "Loaded":
        client.load_collection(collection_name=collection, timeout=timeout)


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
    return create_milvus_client(uri, token, database, timeout_seconds)


def create_milvus_client(
    uri: str,
    token: str,
    database: str,
    timeout_seconds: float,
) -> Any:
    from pymilvus import MilvusClient

    options: dict[str, Any] = {
        "uri": uri,
        "db_name": database,
        "timeout": timeout_seconds,
        # PyMilvus pools channels across clients. Its 10s idle pings can trigger
        # Milvus server GOAWAY/too_many_pings and poison later readiness probes.
        "grpc_options": {
            "grpc.keepalive_time_ms": 300_000,
            "grpc.keepalive_permit_without_calls": False,
        },
    }
    if token:
        options["token"] = token
    return MilvusClient(**options)

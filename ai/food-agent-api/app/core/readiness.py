from __future__ import annotations

from collections.abc import Callable
from concurrent.futures import Future, ThreadPoolExecutor
from typing import Any

import httpx

from app.core.config import Settings

Probe = Callable[[], bool]


def readiness_checks(settings: Settings) -> dict[str, bool]:
    checks = {
        "gateway_identity": bool(settings.gateway_trusted_secret),
        "llm": not settings.llm_router_enabled or bool(settings.llm_api_key),
        "semantic_router": not settings.semantic_router_enabled
        or bool(settings.embedding_api_key),
        "hybrid_rag": not settings.rag_hybrid_enabled
        or (
            settings.es_enabled
            and settings.milvus_enabled
            and bool(settings.embedding_api_key)
        ),
        "mcp": not settings.mcp_enabled or bool(settings.mcp_internal_token),
    }
    if not settings.readiness_runtime_checks_enabled:
        return checks

    probes: dict[str, Probe] = {
        "java_gateway": lambda: _gateway_ready(settings),
    }
    if settings.es_enabled:
        probes["elasticsearch"] = lambda: _elasticsearch_ready(settings)
    if settings.milvus_enabled:
        probes["milvus"] = lambda: _milvus_ready(settings)
    if settings.session_memory_enabled or settings.es_reconcile_distributed_lock_enabled:
        probes["redis"] = lambda: _redis_ready(settings)
    if settings.memory_sync_consumer_enabled or settings.es_sync_consumer_enabled:
        probes["rabbitmq"] = lambda: _rabbitmq_ready(settings)

    with ThreadPoolExecutor(max_workers=len(probes), thread_name_prefix="readiness") as pool:
        futures = {name: pool.submit(probe) for name, probe in probes.items()}
        checks.update({name: _probe_result(future) for name, future in futures.items()})
    return checks


def _probe_result(future: Future[bool]) -> bool:
    try:
        return bool(future.result())
    except Exception:  # noqa: BLE001 - readiness reports dependency failure as data
        return False


def _gateway_ready(settings: Settings) -> bool:
    response = httpx.get(
        f"{settings.java_gateway_base_url.rstrip('/')}/health",
        timeout=settings.readiness_timeout_seconds,
        trust_env=False,
    )
    if response.status_code != 200:
        return False
    payload = response.json()
    return isinstance(payload, dict) and str(payload.get("code")) == "0000"


def _elasticsearch_ready(settings: Settings) -> bool:
    from elasticsearch import Elasticsearch

    client = Elasticsearch(
        settings.es_url,
        request_timeout=settings.readiness_timeout_seconds,
    )
    try:
        return bool(client.ping())
    finally:
        client.close()


def _milvus_ready(settings: Settings) -> bool:
    from pymilvus import MilvusClient

    options: dict[str, Any] = {
        "uri": settings.milvus_uri,
        "db_name": settings.milvus_database,
        "timeout": settings.readiness_timeout_seconds,
    }
    if settings.milvus_token:
        options["token"] = settings.milvus_token
    client = MilvusClient(**options)
    try:
        client.list_collections(timeout=settings.readiness_timeout_seconds)
        return True
    finally:
        client.close()


def _redis_ready(settings: Settings) -> bool:
    from redis import Redis

    client = Redis.from_url(
        settings.redis_url,
        socket_connect_timeout=settings.readiness_timeout_seconds,
        socket_timeout=settings.readiness_timeout_seconds,
    )
    try:
        return bool(client.ping())
    finally:
        client.close()


def _rabbitmq_ready(settings: Settings) -> bool:
    import pika

    parameters = pika.URLParameters(settings.rabbitmq_url)
    parameters.socket_timeout = settings.readiness_timeout_seconds
    parameters.stack_timeout = settings.readiness_timeout_seconds
    parameters.blocked_connection_timeout = settings.readiness_timeout_seconds
    connection = pika.BlockingConnection(parameters)
    try:
        return connection.is_open
    finally:
        connection.close()

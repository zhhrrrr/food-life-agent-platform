"""Explicit, fail-open LangSmith scopes shared by graph execution and evaluations."""
from __future__ import annotations

import hashlib
import logging
import re
import sys
from contextlib import ExitStack, contextmanager
from dataclasses import fields, is_dataclass
from functools import lru_cache
from threading import Lock
from typing import Any

import requests

from langchain_core.tracers.context import tracing_v2_enabled
from langsmith import Client, trace
from langsmith.run_helpers import get_current_run_tree, tracing_context
from pydantic import BaseModel

from app.core.config import Settings

logger = logging.getLogger("food_agent.langsmith")
_clients: list[Client] = []
_client_lock = Lock()
_sensitive = re.compile(r"authorization|cookie|password|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|confirmation[_-]?id|payment[_-]?(?:url|form)|pay[_-]?url|checkout[_-]?(?:url|form)", re.I)
_bearer = re.compile(r"\bBearer\s+[^\s\"'<>;,}]+", re.I)


def redact(value: Any, *, secrets: tuple[str, ...] = ()) -> Any:
    """Copy structured trace data without serializing service objects or credentials."""
    if isinstance(value, BaseModel):
        value = {name: getattr(value, name) for name in type(value).model_fields}
    elif is_dataclass(value) and not isinstance(value, type):
        value = {field.name: getattr(value, field.name) for field in fields(value)}
    if isinstance(value, dict):
        clean = {}
        for key, item in value.items():
            name = str(key)
            if _sensitive.search(name):
                clean[name] = "[REDACTED]"
            elif name in {"user_id", "session_id"} and item:
                clean[name] = item if re.fullmatch(r"sha256:[0-9a-f]{64}", str(item)) else "sha256:" + hashlib.sha256(str(item).encode()).hexdigest()
            else:
                clean[name] = redact(item, secrets=secrets)
        return clean
    if isinstance(value, (list, tuple, set)):
        return [redact(item, secrets=secrets) for item in value]
    if isinstance(value, str):
        value = _bearer.sub("Bearer [REDACTED]", value)
        for secret in secrets:
            if secret:
                value = value.replace(secret, "[REDACTED]")
        return value
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return f"<{type(value).__name__}>"


def trace_inputs(inputs: dict) -> dict:
    # Never trace service instances, which can hold Settings or database handles.
    return redact({key: value for key, value in inputs.items() if key != "self"})


class SanitizedClient(Client):
    """Also filter exception text; input/output hooks do not cover SDK error fields."""
    def __init__(self, **kwargs):
        self._mask_errors = kwargs.get("hide_inputs") is True
        super().__init__(**kwargs)

    def _clean_error(self, kwargs):
        if kwargs.get("error"):
            # GraphInterrupt serializes the entire confirmation payload in its
            # traceback. Never export exception text, even during normal pauses.
            types = re.findall(r"(?m)^([A-Za-z_][A-Za-z0-9_.]*(?:Error|Exception|Interrupt|Exit))(?::|$)", str(kwargs["error"]))
            kwargs["error"] = "[REDACTED]" if self._mask_errors else (types[-1] if types else "ExecutionError")
        return kwargs

    def create_run(self, name, inputs, run_type, **kwargs):
        return super().create_run(name, inputs, run_type, **self._clean_error(kwargs))

    def update_run(self, run_id, **kwargs):
        return super().update_run(run_id, **self._clean_error(kwargs))


@lru_cache(maxsize=4)
def get_langsmith_client(api_key: str, endpoint: str, workspace_id: str,
                        hide_inputs: bool, hide_outputs: bool, trust_env: bool = True) -> Client:
    def sanitize(payload):
        return redact(payload, secrets=(api_key,))
    session = requests.Session()
    session.trust_env = trust_env
    client = SanitizedClient(
        api_key=api_key, api_url=endpoint, workspace_id=workspace_id or None,
        session=session,
        hide_inputs=True if hide_inputs else sanitize,
        hide_outputs=True if hide_outputs else sanitize,
        hide_metadata=sanitize, omit_traced_runtime_info=True,
        timeout_ms=2000,
    )
    with _client_lock:
        _clients.append(client)
    return client


def client_for(settings: Settings) -> Client:
    if not settings.langsmith_api_key.strip():
        raise ValueError("LANGSMITH_API_KEY is required")
    return get_langsmith_client(settings.langsmith_api_key, settings.langsmith_endpoint,
                               settings.langsmith_workspace_id, settings.langsmith_hide_inputs,
                               settings.langsmith_hide_outputs, settings.langsmith_trust_env)


@contextmanager
def observation_scope(settings: Settings, name: str, *, inputs: dict | None = None,
                      metadata: dict | None = None, enabled: bool | None = None):
    """Create a span with the same client for SDK and LangChain descendants.

    Each resumed HTTP turn is a separate trace, associated by business metadata.
    Telemetry setup/teardown must never replace a business result or exception.
    """
    active = settings.langsmith_tracing if enabled is None else enabled
    if not active or not settings.langsmith_api_key.strip():
        if active:
            logger.warning("langsmith_disabled reason=missing_api_key")
        with tracing_context(enabled=False):
            yield None
        return
    stack = ExitStack()
    try:
        client = client_for(settings)
        parent = get_current_run_tree()
        project = parent.session_name if parent else settings.langsmith_project
        tags = ["food-life-agent", settings.langsmith_environment]
        details = {"app_version": settings.app_version, "revision": settings.langsmith_revision,
                   "environment": settings.langsmith_environment, **(metadata or {})}
        stack.enter_context(tracing_context(enabled=True, client=client, project_name=project,
                                            metadata=redact(details), tags=tags))
        run = stack.enter_context(trace(name, inputs=redact(inputs or {}), client=client, project_name=project))
        # LangChain otherwise constructs a global client; .env is loaded into Settings only.
        stack.enter_context(tracing_v2_enabled(project_name=project, client=client))
    except Exception:
        try:
            stack.close()
        except Exception:
            pass
        logger.warning("langsmith_disabled reason=setup_failed")
        with tracing_context(enabled=False):
            yield None
        return
    try:
        yield run
    except BaseException:
        info = sys.exc_info()
        try:
            stack.__exit__(*info)
        except Exception:
            logger.warning("langsmith_teardown_failed")
        raise
    else:
        try:
            stack.close()
        except Exception:
            logger.warning("langsmith_teardown_failed")


def finish_trace(run, outputs: dict) -> None:
    if run is not None:
        try:
            run.end(outputs=redact(outputs))
        except Exception:
            logger.warning("langsmith_output_failed")


def close_langsmith() -> None:
    with _client_lock:
        clients, _clients[:] = list(_clients), []
    for client in clients:
        try:
            client.close(timeout=2)
        except Exception:
            logger.warning("langsmith_flush_failed")
    get_langsmith_client.cache_clear()

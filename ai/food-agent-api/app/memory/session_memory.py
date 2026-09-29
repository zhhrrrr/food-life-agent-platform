from __future__ import annotations

import logging
from time import monotonic
from typing import Any
from uuid import uuid4

import httpx
from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.structured_output import invoke_structured_output

logger = logging.getLogger("food_agent.memory.session")


class SessionTurn(BaseModel):
    turn_id: str = ""
    sequence_no: int = 0
    user: str = Field(min_length=1, max_length=4000)
    assistant: str = Field(default="", max_length=4000)
    intent: str = Field(default="", max_length=32)
    run_id: str = ""


class SessionContext(BaseModel):
    session_id: str
    summary: str = ""
    summary_through_seq: int = 0
    version: int = 0
    turns: list[SessionTurn] = Field(default_factory=list)

    def history(self, limit: int) -> list[SessionTurn]:
        turns = self.turns[-max(1, limit) :]
        if not self.summary:
            return turns
        summary_turn = SessionTurn(
            turn_id="summary",
            sequence_no=self.summary_through_seq,
            user=f"历史对话摘要：{self.summary}",
            intent="summary",
        )
        return [summary_turn, *turns]


class SummaryOutput(BaseModel):
    summary: str = Field(min_length=1, max_length=2000)


class AgentMemoryClient:
    def __init__(self, settings: Settings | None = None, client: Any | None = None) -> None:
        self.settings = settings or get_settings()
        self._client = client

    def load(self, session_id: str, authorization: str) -> SessionContext:
        data = self._request(
            "GET",
            f"/api/user/agent-memory/sessions/{session_id}",
            authorization,
            params={"limit": self.settings.session_memory_max_turns},
        )
        return _session_context(data, session_id)

    def append(
        self,
        session_id: str,
        authorization: str,
        *,
        turn_id: str,
        user: str,
        assistant: str,
        intent: str,
        run_id: str,
    ) -> SessionContext:
        data = self._request(
            "POST",
            f"/api/user/agent-memory/sessions/{session_id}/turns",
            authorization,
            json={
                "turnId": turn_id,
                "user": user[:4000],
                "assistant": assistant[:4000],
                "intent": intent[:32],
                "runId": run_id[:64],
            },
        )
        return _session_context(data, session_id)

    def update_summary(
        self,
        session_id: str,
        authorization: str,
        *,
        summary: str,
        summary_through_seq: int,
        expected_version: int,
    ) -> bool:
        data = self._request(
            "PUT",
            f"/api/user/agent-memory/sessions/{session_id}/summary",
            authorization,
            json={
                "summary": summary[:2000],
                "summaryThroughSeq": summary_through_seq,
                "expectedVersion": expected_version,
            },
        )
        return bool(data.get("updated"))

    def load_profile(self, authorization: str) -> list[dict[str, Any]]:
        data = self._request("GET", "/api/user/agent-memory/profile", authorization)
        return data if isinstance(data, list) else []

    def upsert_profile(
        self,
        authorization: str,
        *,
        memory_type: str,
        memory_key: str,
        value_json: str,
        source_turn_id: str,
    ) -> None:
        self._request(
            "PUT",
            f"/api/user/agent-memory/profile/{memory_type}/{memory_key}",
            authorization,
            json={"valueJson": value_json, "sourceTurnId": source_turn_id},
        )

    def save_event(self, authorization: str, event: dict[str, Any]) -> None:
        self._request("POST", "/api/user/agent-memory/events", authorization, json=event)

    def _request(self, method: str, path: str, authorization: str, **kwargs: Any) -> Any:
        client = self._client or httpx.Client(
            base_url=self.settings.java_gateway_base_url,
            timeout=self.settings.memory_java_timeout_seconds,
        )
        response = client.request(
            method,
            path,
            headers={"Authorization": authorization},
            **kwargs,
        )
        response.raise_for_status()
        body = response.json()
        if str(body.get("code")) != "0000":
            raise RuntimeError(str(body.get("message") or "agent memory API failed"))
        return body.get("data") or {}


class SessionSummaryService:
    def __init__(self, settings: Settings | None = None, chat_model: Any | None = None) -> None:
        self.settings = settings or get_settings()
        self.chat_model = chat_model

    def should_summarize(self, context: SessionContext) -> bool:
        unsummarized = [
            turn for turn in context.turns if turn.sequence_no > context.summary_through_seq
        ]
        chars = sum(len(turn.user) + len(turn.assistant) for turn in unsummarized)
        return len(unsummarized) >= self.settings.session_summary_trigger_turns or chars >= self.settings.session_summary_trigger_chars

    def summarize(self, context: SessionContext) -> str:
        transcript = "\n".join(
            f"用户：{turn.user}\n助手：{turn.assistant}" for turn in context.turns
        )
        try:
            result = invoke_structured_output(
                settings=self.settings,
                schema=SummaryOutput,
                chat_model=self.chat_model,
                messages=[
                    ("system", "压缩对话历史。保留用户明确偏好、约束、已讨论对象和未完成事项；历史价格、库存和订单状态必须标记为历史讨论，不得写成当前事实。"),
                    ("user", f"已有摘要：{context.summary or '无'}\n新增对话：\n{transcript}"),
                ],
            )
            return result.summary
        except Exception as exc:  # noqa: BLE001 - summary is an optimization
            logger.warning("session_summary_llm_fallback error=%s", type(exc).__name__)
            compact = "；".join(turn.user for turn in context.turns[-6:])
            return f"{context.summary}；{compact}".strip("；")[:2000]


class SessionMemoryService:
    """MySQL-backed session memory with Redis as a disposable hot view."""

    def __init__(
        self,
        settings: Settings | None = None,
        *,
        redis_client: Any | None = None,
        java_client: AgentMemoryClient | None = None,
        summary_service: SessionSummaryService | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self._redis_client = redis_client
        self.java_client = java_client or AgentMemoryClient(self.settings)
        self.summary_service = summary_service or SessionSummaryService(self.settings)
        self._retry_after = 0.0

    def load(self, session_id: str, *, user_id: str = "", authorization: str = "") -> tuple[SessionContext, str]:
        cached = self._cache_get(session_id, user_id)
        if cached is not None:
            return cached, "cache"
        if user_id and authorization:
            try:
                context = self.java_client.load(session_id, authorization)
                self._cache_set(context, user_id)
                return context, "mysql"
            except Exception as exc:  # noqa: BLE001 - memory must not block chat
                logger.warning("session_memory_load_degraded session_id=%s error=%s", session_id, type(exc).__name__)
        return SessionContext(session_id=session_id), "empty"

    def append(
        self,
        session_id: str,
        *,
        user_id: str = "",
        authorization: str = "",
        user: str,
        assistant: str,
        intent: str,
        run_id: str,
    ) -> str:
        turn_id = uuid4().hex
        if user_id and authorization:
            try:
                context = self.java_client.append(
                    session_id,
                    authorization,
                    turn_id=turn_id,
                    user=user,
                    assistant=assistant,
                    intent=intent,
                    run_id=run_id,
                )
                context = self._maybe_summarize(context, authorization)
                self._cache_set(context, user_id)
                return "durable"
            except Exception as exc:  # noqa: BLE001 - preserve answer during dependency outage
                logger.warning("session_memory_append_degraded session_id=%s error=%s", session_id, type(exc).__name__)

        context = self._cache_get(session_id, user_id) or SessionContext(session_id=session_id)
        sequence = max((turn.sequence_no for turn in context.turns), default=0) + 2
        context.turns.append(SessionTurn(
            turn_id=turn_id,
            sequence_no=sequence,
            user=user[:4000],
            assistant=assistant[:4000],
            intent=intent[:32],
            run_id=run_id[:64],
        ))
        context.turns = context.turns[-self.settings.session_memory_max_turns :]
        self._cache_set(context, user_id)
        return "cache_only" if self.settings.session_memory_enabled else "disabled"

    def _maybe_summarize(self, context: SessionContext, authorization: str) -> SessionContext:
        if not self.settings.session_summary_enabled or not self.summary_service.should_summarize(context):
            return context
        summary = self.summary_service.summarize(context)
        through = max((turn.sequence_no for turn in context.turns), default=context.summary_through_seq)
        if self.java_client.update_summary(
            context.session_id,
            authorization,
            summary=summary,
            summary_through_seq=through,
            expected_version=context.version,
        ):
            context.summary = summary
            context.summary_through_seq = through
            context.version += 1
        return context

    def _cache_get(self, session_id: str, user_id: str) -> SessionContext | None:
        if not self.settings.session_memory_enabled or monotonic() < self._retry_after:
            return None
        try:
            value = self._client().get(self._key(session_id, user_id))
            self._retry_after = 0.0
            return SessionContext.model_validate_json(value) if value else None
        except Exception as exc:  # noqa: BLE001
            self._retry_after = monotonic() + 10
            logger.warning("session_cache_read_failed error=%s", type(exc).__name__)
            return None

    def _cache_set(self, context: SessionContext, user_id: str) -> None:
        if not self.settings.session_memory_enabled or monotonic() < self._retry_after:
            return
        try:
            self._client().setex(
                self._key(context.session_id, user_id),
                self.settings.session_memory_ttl_seconds,
                context.model_dump_json(),
            )
            self._retry_after = 0.0
        except Exception as exc:  # noqa: BLE001
            self._retry_after = monotonic() + 10
            logger.warning("session_cache_write_failed error=%s", type(exc).__name__)

    def _key(self, session_id: str, user_id: str) -> str:
        owner = user_id or "anonymous"
        return f"{self.settings.session_memory_key_prefix}{owner}:{session_id}"

    def _client(self) -> Any:
        if self._redis_client is None:
            from redis import Redis
            self._redis_client = Redis.from_url(
                self.settings.redis_url,
                decode_responses=True,
                socket_connect_timeout=1,
                socket_timeout=1,
            )
        return self._redis_client


def should_use_session_history(message: str) -> bool:
    return any(marker in message.casefold() for marker in (
        "那个", "这个", "那", "呢", "它", "还有", "刚才", "上一个", "前面", "一样", "then", "previous",
    ))


def _session_context(data: Any, session_id: str) -> SessionContext:
    if not isinstance(data, dict):
        return SessionContext(session_id=session_id)
    turns = []
    for item in data.get("turns") or []:
        turns.append(SessionTurn(
            turn_id=str(item.get("turnId") or ""),
            sequence_no=int(item.get("sequenceNo") or 0),
            user=str(item.get("user") or ""),
            assistant=str(item.get("assistant") or ""),
            intent=str(item.get("intent") or ""),
            run_id=str(item.get("runId") or ""),
        ))
    return SessionContext(
        session_id=str(data.get("sessionId") or session_id),
        summary=str(data.get("summary") or ""),
        summary_through_seq=int(data.get("summaryThroughSeq") or 0),
        version=int(data.get("version") or 0),
        turns=turns,
    )

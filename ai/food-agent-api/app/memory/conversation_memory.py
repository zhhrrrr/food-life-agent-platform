import logging
from time import monotonic
from typing import Any

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings

logger = logging.getLogger("food_agent.memory.conversation")


class ConversationTurn(BaseModel):
    user: str = Field(min_length=1, max_length=4000)
    assistant: str = Field(default="", max_length=4000)
    intent: str = Field(default="", max_length=32)


class RedisConversationMemory:
    """Bounded, expiring conversation memory shared by all API instances."""

    def __init__(
        self,
        settings: Settings | None = None,
        redis_client: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self._redis_client = redis_client
        self._retry_after = 0.0

    def load(self, conversation_id: str) -> list[ConversationTurn]:
        if not self.settings.conversation_memory_enabled:
            return []
        if monotonic() < self._retry_after:
            return []
        try:
            values = self._client().lrange(self._key(conversation_id), 0, -1)
            turns: list[ConversationTurn] = []
            for value in values:
                try:
                    turns.append(ConversationTurn.model_validate_json(value))
                except Exception:
                    logger.warning(
                        "conversation_memory_skipped_invalid_turn conversation_id=%s",
                        conversation_id,
                    )
            self._retry_after = 0.0
            return turns[-self._max_turns :]
        except Exception as exc:
            self._mark_failure()
            logger.warning(
                "conversation_memory_load_failed conversation_id=%s error=%s",
                conversation_id,
                type(exc).__name__,
            )
            return []

    def append(
        self,
        conversation_id: str,
        *,
        user: str,
        assistant: str,
        intent: str,
    ) -> None:
        if not self.settings.conversation_memory_enabled:
            return
        if monotonic() < self._retry_after:
            return
        turn = ConversationTurn(
            user=user[:4000],
            assistant=assistant[:4000],
            intent=intent[:32],
        )
        key = self._key(conversation_id)
        try:
            pipeline = self._client().pipeline(transaction=True)
            pipeline.rpush(key, turn.model_dump_json())
            pipeline.ltrim(key, -self._max_turns, -1)
            pipeline.expire(key, self._ttl_seconds)
            pipeline.execute()
            self._retry_after = 0.0
        except Exception as exc:
            self._mark_failure()
            logger.warning(
                "conversation_memory_append_failed conversation_id=%s error=%s",
                conversation_id,
                type(exc).__name__,
            )

    @property
    def _max_turns(self) -> int:
        return max(1, self.settings.conversation_memory_max_turns)

    @property
    def _ttl_seconds(self) -> int:
        return max(60, self.settings.conversation_memory_ttl_seconds)

    def _key(self, conversation_id: str) -> str:
        return f"{self.settings.conversation_memory_key_prefix}{conversation_id}"

    def _client(self) -> Any:
        if self._redis_client is None:
            from redis import Redis

            self._redis_client = Redis.from_url(
                self.settings.redis_url,
                decode_responses=True,
                socket_connect_timeout=1.0,
                socket_timeout=1.0,
            )
        return self._redis_client

    def _mark_failure(self) -> None:
        self._retry_after = monotonic() + 10.0


def should_use_conversation_history(message: str) -> bool:
    follow_up_markers = (
        "那",
        "这个",
        "那个",
        "它",
        "还有",
        "再看看",
        "刚才",
        "上一个",
        "前面",
        "一样",
        "呢",
    )
    return any(marker in message for marker in follow_up_markers)

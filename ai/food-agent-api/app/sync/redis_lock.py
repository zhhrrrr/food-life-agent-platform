import logging
import threading
from typing import Any
from uuid import uuid4

from app.core.config import Settings

logger = logging.getLogger("food_agent.sync.redis_lock")


class RedisDistributedLock:
    _RELEASE_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    end
    return 0
    """
    # 续期
    _RENEW_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("expire", KEYS[1], ARGV[2])
    end
    return 0
    """

    def __init__(
        self,
        redis_url: str,
        lock_key: str,
        ttl_seconds: int,
        enabled: bool = True,
        name: str = "redis-distributed-lock",
    ) -> None:
        self.redis_url = redis_url
        self.lock_key = lock_key
        self.ttl_seconds = ttl_seconds
        self.enabled = enabled
        self.name = name
        self.token = str(uuid4())
        self._client: Any | None = None
        self._renew_thread: threading.Thread | None = None
        self._stopping = threading.Event()

    @classmethod
    def for_es_reconciliation(cls, settings: Settings) -> "RedisDistributedLock":
        return cls(
            redis_url=settings.redis_url,
            lock_key=settings.es_reconcile_lock_key,
            ttl_seconds=settings.es_reconcile_lock_ttl_seconds,
            enabled=settings.es_reconcile_distributed_lock_enabled,
            name="es-reconciliation",
        )

    def acquire(self) -> bool:
        if not self.enabled:
            return True

        client = self._redis_client()
        if client is None:
            return False

        acquired = bool(
            client.set(
                self.lock_key,
                self.token,
                nx=True,
                ex=self._ttl_seconds(),
            )
        )
        if acquired:
            self._start_renewal()
        return acquired

    def release(self) -> None:
        self._stopping.set()
        if self._renew_thread and self._renew_thread.is_alive():
            self._renew_thread.join(timeout=2)
        if not self.enabled:
            return

        client = self._redis_client()
        if client is None:
            return
        try:
            client.eval(self._RELEASE_SCRIPT, 1, self.lock_key, self.token)
        except Exception:
            logger.exception("%s_release_failed", self.name)

    def _redis_client(self) -> Any | None:
        if self._client is not None:
            return self._client
        try:
            import redis
        except ImportError:
            logger.warning("redis package is not installed, %s unavailable", self.name)
            return None
        try:
            self._client = redis.Redis.from_url(self.redis_url, decode_responses=True)
            return self._client
        except Exception:
            logger.exception("%s_redis_client_failed", self.name)
            return None

    def _start_renewal(self) -> None:
        self._renew_thread = threading.Thread(
            target=self._renew_loop,
            name=f"{self.name}-renewal",
            daemon=True,
        )
        self._renew_thread.start()

    def _renew_loop(self) -> None:
        wait_seconds = max(1, self._ttl_seconds() // 3)
        while not self._stopping.wait(wait_seconds):
            client = self._redis_client()
            if client is None:
                return
            try:
                renewed = client.eval(
                    self._RENEW_SCRIPT,
                    1,
                    self.lock_key,
                    self.token,
                    self._ttl_seconds(),
                )
            except Exception:
                logger.exception("%s_renew_failed", self.name)
                return
            if not renewed:
                logger.warning("%s_lost", self.name)
                return

    def _ttl_seconds(self) -> int:
        return max(30, self.ttl_seconds)

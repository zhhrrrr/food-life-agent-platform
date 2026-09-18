import logging
import threading
from collections.abc import Callable
from typing import Any
from uuid import uuid4

from app.core.config import Settings, get_settings
from app.indexing.indexer import SearchIndexer

logger = logging.getLogger("food_agent.sync.es_reconciliation")


class RedisReconciliationLock:
    _RELEASE_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    end
    return 0
    """
    _RENEW_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("expire", KEYS[1], ARGV[2])
    end
    return 0
    """

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.token = str(uuid4())
        self._client: Any | None = None
        self._renew_thread: threading.Thread | None = None
        self._stopping = threading.Event()

    def acquire(self) -> bool:
        if not self.settings.es_reconcile_distributed_lock_enabled:
            return True

        client = self._redis_client()
        if client is None:
            return False

        acquired = bool(
            client.set(
                self.settings.es_reconcile_lock_key,
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
        if not self.settings.es_reconcile_distributed_lock_enabled:
            return

        client = self._redis_client()
        if client is None:
            return
        try:
            client.eval(self._RELEASE_SCRIPT, 1, self.settings.es_reconcile_lock_key, self.token)
        except Exception:
            logger.exception("es_reconciliation_lock_release_failed")

    def _redis_client(self) -> Any | None:
        if self._client is not None:
            return self._client
        try:
            import redis
        except ImportError:
            logger.warning("redis package is not installed, ES reconciliation lock unavailable")
            return None
        try:
            self._client = redis.Redis.from_url(self.settings.redis_url, decode_responses=True)
            return self._client
        except Exception:
            logger.exception("es_reconciliation_redis_client_failed")
            return None

    def _start_renewal(self) -> None:
        self._renew_thread = threading.Thread(
            target=self._renew_loop,
            name="es-reconciliation-lock-renewal",
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
                    self.settings.es_reconcile_lock_key,
                    self.token,
                    self._ttl_seconds(),
                )
            except Exception:
                logger.exception("es_reconciliation_lock_renew_failed")
                return
            if not renewed:
                logger.warning("es_reconciliation_lock_lost")
                return

    def _ttl_seconds(self) -> int:
        return max(30, self.settings.es_reconcile_lock_ttl_seconds)


class EsReconciliationScheduler:
    def __init__(
        self,
        settings: Settings | None = None,
        reconcile_factory: Callable[[], SearchIndexer] | None = None,
        lock_factory: Callable[[], RedisReconciliationLock] | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.reconcile_factory = reconcile_factory or (lambda: SearchIndexer(settings=self.settings))
        self.lock_factory = lock_factory or (lambda: RedisReconciliationLock(self.settings))
        self._thread: threading.Thread | None = None
        self._stopping = threading.Event()
        self._start_lock = threading.Lock()
        self._run_lock = threading.Lock()

    def start(self) -> None:
        if not self.settings.es_reconcile_enabled:
            return
        with self._start_lock:
            if self.is_running:
                return
            self._stopping.clear()
            self._thread = threading.Thread(
                target=self._run,
                name="es-reconciliation",
                daemon=True,
            )
            self._thread.start()

    def stop(self) -> None:
        self._stopping.set()
        if self._thread and self._thread.is_alive() and threading.current_thread() != self._thread:
            self._thread.join(timeout=5)

    @property
    def is_running(self) -> bool:
        return self._thread is not None and self._thread.is_alive()

    def _run(self) -> None:
        interval_seconds = self._interval_seconds()
        logger.info(
            "es_reconciliation_started interval_seconds=%s run_on_startup=%s "
            "page_size=%s prune_stale=%s",
            interval_seconds,
            self.settings.es_reconcile_run_on_startup,
            self.settings.es_reconcile_page_size,
            self.settings.es_reconcile_prune_stale,
        )

        if self.settings.es_reconcile_run_on_startup and not self._stopping.is_set():
            self._run_once()

        while not self._stopping.wait(interval_seconds):
            self._run_once()

    def _run_once(self) -> None:
        if not self._run_lock.acquire(blocking=False):
            logger.info("es_reconciliation_skipped reason=previous_run_still_active")
            return
        lock = self.lock_factory()
        lock_acquired = False
        try:
            lock_acquired = lock.acquire()
            if not lock_acquired:
                logger.info("es_reconciliation_skipped reason=distributed_lock_busy")
                return
            result = self.reconcile_factory().reconcile()
            logger.info(
                "es_reconciliation_finished fetched=%s checked=%s upserted=%s "
                "unchanged=%s deleted=%s failed=%s",
                result.fetched,
                result.checked,
                result.upserted,
                result.unchanged,
                result.deleted,
                result.failed,
            )
        except Exception:
            logger.exception("es_reconciliation_failed")
        finally:
            if lock_acquired:
                lock.release()
            self._run_lock.release()

    def _interval_seconds(self) -> int:
        return max(1, self.settings.es_reconcile_interval_seconds)


_scheduler: EsReconciliationScheduler | None = None


def start_es_reconciliation(settings: Settings | None = None) -> None:
    global _scheduler
    if _scheduler is not None and _scheduler.is_running:
        return
    _scheduler = EsReconciliationScheduler(settings=settings)
    _scheduler.start()


def stop_es_reconciliation() -> None:
    if _scheduler is not None:
        _scheduler.stop()

import logging
import threading
from collections.abc import Callable

from app.core.config import Settings, get_settings
from app.indexing.indexer import SearchIndexer
from app.sync.redis_lock import RedisDistributedLock

logger = logging.getLogger("food_agent.sync.es_reconciliation")


class EsReconciliationScheduler:
    def __init__(
        self,
        settings: Settings | None = None,
        reconcile_factory: Callable[[], SearchIndexer] | None = None,
        lock_factory: Callable[[], RedisDistributedLock] | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.reconcile_factory = reconcile_factory or (lambda: SearchIndexer(settings=self.settings))
        self.lock_factory = lock_factory or (
            lambda: RedisDistributedLock.for_es_reconciliation(self.settings)
        )
        self._thread: threading.Thread | None = None
        self._stopping = threading.Event()

    def start(self) -> None:
        if not self.settings.es_reconcile_enabled:
            return
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

import logging
import threading

from app.core.config import Settings, get_settings
from app.indexing.indexer import SearchIndexer

logger = logging.getLogger("food_agent.sync.es_reconciliation")


class EsReconciliationScheduler:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self._thread: threading.Thread | None = None
        self._stopping = threading.Event()

    def start(self) -> None:
        if not self.settings.es_reconcile_enabled:
            return
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(
            target=self._run,
            name="es-reconciliation",
            daemon=True,
        )
        self._thread.start()

    def stop(self) -> None:
        self._stopping.set()

    def _run(self) -> None:
        logger.info(
            "es_reconciliation_started interval_seconds=%s page_size=%s prune_stale=%s",
            self.settings.es_reconcile_interval_seconds,
            self.settings.es_reconcile_page_size,
            self.settings.es_reconcile_prune_stale,
        )

        while not self._stopping.wait(self.settings.es_reconcile_interval_seconds):
            try:
                result = SearchIndexer(settings=self.settings).reconcile()
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


_scheduler: EsReconciliationScheduler | None = None


def start_es_reconciliation(settings: Settings | None = None) -> None:
    global _scheduler
    _scheduler = EsReconciliationScheduler(settings=settings)
    _scheduler.start()


def stop_es_reconciliation() -> None:
    if _scheduler is not None:
        _scheduler.stop()

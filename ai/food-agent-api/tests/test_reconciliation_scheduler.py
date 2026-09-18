from app.core.config import Settings
from app.sync.es_reconciliation import EsReconciliationScheduler


class FakeReconcileResult:
    fetched = 0
    checked = 0
    upserted = 0
    unchanged = 0
    deleted = 0
    failed = 0


class FakeIndexer:
    def __init__(self, calls: list[str], fail: bool = False) -> None:
        self.calls = calls
        self.fail = fail

    def reconcile(self) -> FakeReconcileResult:
        self.calls.append("reconcile")
        if self.fail:
            raise RuntimeError("boom")
        return FakeReconcileResult()


def test_scheduler_does_not_start_duplicate_thread() -> None:
    calls: list[str] = []
    scheduler = EsReconciliationScheduler(
        settings=Settings(
            es_reconcile_enabled=True,
            es_reconcile_interval_seconds=3600,
            es_reconcile_run_on_startup=False,
        ),
        reconcile_factory=lambda: FakeIndexer(calls),
    )

    scheduler.start()
    first_thread = scheduler._thread
    scheduler.start()

    assert scheduler.is_running is True
    assert scheduler._thread is first_thread
    assert calls == []

    scheduler.stop()


def test_scheduler_normalizes_interval_seconds() -> None:
    scheduler = EsReconciliationScheduler(
        settings=Settings(
            es_reconcile_enabled=True,
            es_reconcile_interval_seconds=0,
        )
    )

    assert scheduler._interval_seconds() == 1


def test_scheduler_run_once_releases_lock_after_failure() -> None:
    calls: list[str] = []
    should_fail = True

    def factory() -> FakeIndexer:
        return FakeIndexer(calls, fail=should_fail)

    scheduler = EsReconciliationScheduler(
        settings=Settings(es_reconcile_enabled=True),
        reconcile_factory=factory,
    )

    scheduler._run_once()
    should_fail = False
    scheduler._run_once()

    assert calls == ["reconcile", "reconcile"]

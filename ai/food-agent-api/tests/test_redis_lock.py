from app.core.config import Settings
from app.sync.redis_lock import RedisDistributedLock


def test_redis_lock_is_noop_when_disabled() -> None:
    lock = RedisDistributedLock(
        redis_url="redis://127.0.0.1:6379/0",
        lock_key="test-lock",
        ttl_seconds=1,
        enabled=False,
    )

    assert lock.acquire() is True
    lock.release()


def test_redis_lock_ttl_has_minimum() -> None:
    lock = RedisDistributedLock(
        redis_url="redis://127.0.0.1:6379/0",
        lock_key="test-lock",
        ttl_seconds=1,
        enabled=False,
    )

    assert lock._ttl_seconds() == 30


def test_redis_lock_builds_from_reconciliation_settings() -> None:
    settings = Settings(
        redis_url="redis://127.0.0.1:6379/2",
        es_reconcile_distributed_lock_enabled=True,
        es_reconcile_lock_key="custom-lock",
        es_reconcile_lock_ttl_seconds=45,
    )

    lock = RedisDistributedLock.for_es_reconciliation(settings)

    assert lock.redis_url == "redis://127.0.0.1:6379/2"
    assert lock.lock_key == "custom-lock"
    assert lock._ttl_seconds() == 45
    assert lock.enabled is True

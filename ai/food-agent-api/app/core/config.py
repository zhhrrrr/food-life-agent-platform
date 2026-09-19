import os
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel


class Settings(BaseModel):
    app_name: str = "Food Life Agent API"
    app_version: str = "0.7.0"
    app_port: int = 8500
    log_level: str = "INFO"
    java_gateway_base_url: str = "http://127.0.0.1:8080"
    java_business_base_url: str = "http://127.0.0.1:8201"
    java_trade_base_url: str = "http://127.0.0.1:8301"
    es_enabled: bool = False
    es_url: str = "http://127.0.0.1:9200"
    es_index_shop: str = "food_shop_index"
    es_index_package: str = "food_package_index"
    es_index_rule: str = "food_rule_index"
    es_index_review: str = "food_review_index"
    es_index_activity: str = "food_activity_index"
    es_index_coupon: str = "food_coupon_index"
    rabbitmq_url: str = "amqp://guest:guest@127.0.0.1:5672/%2F"
    redis_url: str = "redis://127.0.0.1:6379/0"
    es_sync_consumer_enabled: bool = False
    es_sync_business_queue: str = "food.business.search.sync.queue"
    es_sync_trade_queue: str = "food.trade.search.sync.queue"
    es_reconcile_enabled: bool = False
    es_reconcile_interval_seconds: int = 900
    es_reconcile_run_on_startup: bool = True
    es_reconcile_distributed_lock_enabled: bool = False
    es_reconcile_lock_key: str = "food-agent-api:es-reconciliation:lock"
    es_reconcile_lock_ttl_seconds: int = 1800
    es_reconcile_page_size: int = 200
    es_reconcile_prune_stale: bool = True
    es_reconcile_scan_size: int = 10000


def _read_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def _get_value(dotenv_values: dict[str, str], key: str, default: str) -> str:
    return os.getenv(key, dotenv_values.get(key, default))


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    dotenv_values = _read_dotenv(Path.cwd() / ".env")
    defaults = Settings()

    return Settings(
        app_name=_get_value(dotenv_values, "APP_NAME", defaults.app_name),
        app_version=_get_value(dotenv_values, "APP_VERSION", defaults.app_version),
        app_port=int(_get_value(dotenv_values, "APP_PORT", str(defaults.app_port))),
        log_level=_get_value(dotenv_values, "LOG_LEVEL", defaults.log_level),
        java_gateway_base_url=_get_value(
            dotenv_values,
            "JAVA_GATEWAY_BASE_URL",
            defaults.java_gateway_base_url,
        ),
        java_business_base_url=_get_value(
            dotenv_values,
            "JAVA_BUSINESS_BASE_URL",
            defaults.java_business_base_url,
        ),
        java_trade_base_url=_get_value(
            dotenv_values,
            "JAVA_TRADE_BASE_URL",
            defaults.java_trade_base_url,
        ),
        es_enabled=_get_value(
            dotenv_values,
            "ES_ENABLED",
            str(defaults.es_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_url=_get_value(dotenv_values, "ES_URL", defaults.es_url),
        es_index_shop=_get_value(dotenv_values, "ES_INDEX_SHOP", defaults.es_index_shop),
        es_index_package=_get_value(
            dotenv_values,
            "ES_INDEX_PACKAGE",
            defaults.es_index_package,
        ),
        es_index_rule=_get_value(dotenv_values, "ES_INDEX_RULE", defaults.es_index_rule),
        es_index_review=_get_value(dotenv_values, "ES_INDEX_REVIEW", defaults.es_index_review),
        es_index_activity=_get_value(
            dotenv_values,
            "ES_INDEX_ACTIVITY",
            defaults.es_index_activity,
        ),
        es_index_coupon=_get_value(dotenv_values, "ES_INDEX_COUPON", defaults.es_index_coupon),
        rabbitmq_url=_get_value(dotenv_values, "RABBITMQ_URL", defaults.rabbitmq_url),
        redis_url=_get_value(dotenv_values, "REDIS_URL", defaults.redis_url),
        es_sync_consumer_enabled=_get_value(
            dotenv_values,
            "ES_SYNC_CONSUMER_ENABLED",
            str(defaults.es_sync_consumer_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_sync_business_queue=_get_value(
            dotenv_values,
            "ES_SYNC_BUSINESS_QUEUE",
            defaults.es_sync_business_queue,
        ),
        es_sync_trade_queue=_get_value(
            dotenv_values,
            "ES_SYNC_TRADE_QUEUE",
            defaults.es_sync_trade_queue,
        ),
        es_reconcile_enabled=_get_value(
            dotenv_values,
            "ES_RECONCILE_ENABLED",
            str(defaults.es_reconcile_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_reconcile_interval_seconds=int(
            _get_value(
                dotenv_values,
                "ES_RECONCILE_INTERVAL_SECONDS",
                str(defaults.es_reconcile_interval_seconds),
            )
        ),
        es_reconcile_run_on_startup=_get_value(
            dotenv_values,
            "ES_RECONCILE_RUN_ON_STARTUP",
            str(defaults.es_reconcile_run_on_startup),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_reconcile_distributed_lock_enabled=_get_value(
            dotenv_values,
            "ES_RECONCILE_DISTRIBUTED_LOCK_ENABLED",
            str(defaults.es_reconcile_distributed_lock_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_reconcile_lock_key=_get_value(
            dotenv_values,
            "ES_RECONCILE_LOCK_KEY",
            defaults.es_reconcile_lock_key,
        ),
        es_reconcile_lock_ttl_seconds=int(
            _get_value(
                dotenv_values,
                "ES_RECONCILE_LOCK_TTL_SECONDS",
                str(defaults.es_reconcile_lock_ttl_seconds),
            )
        ),
        es_reconcile_page_size=int(
            _get_value(
                dotenv_values,
                "ES_RECONCILE_PAGE_SIZE",
                str(defaults.es_reconcile_page_size),
            )
        ),
        es_reconcile_prune_stale=_get_value(
            dotenv_values,
            "ES_RECONCILE_PRUNE_STALE",
            str(defaults.es_reconcile_prune_stale),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_reconcile_scan_size=int(
            _get_value(
                dotenv_values,
                "ES_RECONCILE_SCAN_SIZE",
                str(defaults.es_reconcile_scan_size),
            )
        ),
    )

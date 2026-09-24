import os
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel, Field


class Settings(BaseModel):
    app_name: str = "Food Life Agent API"
    app_version: str = "0.13.0"
    app_port: int = 8500
    log_level: str = "INFO"
    # 本地env 文件中配置的llm_router_enabled优先级高于此处的默认值
    llm_router_enabled: bool = False
    llm_provider: str = "openai"
    llm_model: str = "gpt-4o-mini"
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_temperature: float = 0.0
    llm_timeout_seconds: float = 10.0
    semantic_router_enabled: bool = False
    embedding_provider: str = "openai"
    embedding_model: str = "text-embedding-3-small"
    embedding_api_key: str = ""
    embedding_base_url: str = ""
    embedding_timeout_seconds: float = Field(default=10.0, gt=0)
    semantic_router_high_threshold: float = Field(default=0.82, ge=0.0, le=1.0)
    semantic_router_min_margin: float = Field(default=0.05, ge=0.0, le=1.0)
    semantic_router_failure_cooldown_seconds: float = Field(default=30.0, ge=1.0, le=300.0)
    conversation_memory_enabled: bool = False
    conversation_memory_ttl_seconds: int = Field(default=86400, ge=60)
    conversation_memory_max_turns: int = Field(default=8, ge=1, le=50)
    conversation_memory_key_prefix: str = "food-agent-api:conversation:"
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
        llm_router_enabled=_get_value(
            dotenv_values,
            "LLM_ROUTER_ENABLED",
            str(defaults.llm_router_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        llm_provider=_get_value(dotenv_values, "LLM_PROVIDER", defaults.llm_provider),
        llm_model=_get_value(dotenv_values, "LLM_MODEL", defaults.llm_model),
        llm_api_key=_get_value(dotenv_values, "LLM_API_KEY", defaults.llm_api_key),
        llm_base_url=_get_value(dotenv_values, "LLM_BASE_URL", defaults.llm_base_url),
        llm_temperature=float(
            _get_value(dotenv_values, "LLM_TEMPERATURE", str(defaults.llm_temperature))
        ),
        llm_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "LLM_TIMEOUT_SECONDS",
                str(defaults.llm_timeout_seconds),
            )
        ),
        semantic_router_enabled=_get_value(
            dotenv_values,
            "SEMANTIC_ROUTER_ENABLED",
            str(defaults.semantic_router_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        embedding_provider=_get_value(
            dotenv_values,
            "EMBEDDING_PROVIDER",
            defaults.embedding_provider,
        ),
        embedding_model=_get_value(
            dotenv_values,
            "EMBEDDING_MODEL",
            defaults.embedding_model,
        ),
        embedding_api_key=_get_value(
            dotenv_values,
            "EMBEDDING_API_KEY",
            defaults.embedding_api_key,
        ),
        embedding_base_url=_get_value(
            dotenv_values,
            "EMBEDDING_BASE_URL",
            defaults.embedding_base_url,
        ),
        embedding_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "EMBEDDING_TIMEOUT_SECONDS",
                str(defaults.embedding_timeout_seconds),
            )
        ),
        semantic_router_high_threshold=float(
            _get_value(
                dotenv_values,
                "SEMANTIC_ROUTER_HIGH_THRESHOLD",
                str(defaults.semantic_router_high_threshold),
            )
        ),
        semantic_router_min_margin=float(
            _get_value(
                dotenv_values,
                "SEMANTIC_ROUTER_MIN_MARGIN",
                str(defaults.semantic_router_min_margin),
            )
        ),
        semantic_router_failure_cooldown_seconds=float(
            _get_value(
                dotenv_values,
                "SEMANTIC_ROUTER_FAILURE_COOLDOWN_SECONDS",
                str(defaults.semantic_router_failure_cooldown_seconds),
            )
        ),
        conversation_memory_enabled=_get_value(
            dotenv_values,
            "CONVERSATION_MEMORY_ENABLED",
            str(defaults.conversation_memory_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        conversation_memory_ttl_seconds=int(
            _get_value(
                dotenv_values,
                "CONVERSATION_MEMORY_TTL_SECONDS",
                str(defaults.conversation_memory_ttl_seconds),
            )
        ),
        conversation_memory_max_turns=int(
            _get_value(
                dotenv_values,
                "CONVERSATION_MEMORY_MAX_TURNS",
                str(defaults.conversation_memory_max_turns),
            )
        ),
        conversation_memory_key_prefix=_get_value(
            dotenv_values,
            "CONVERSATION_MEMORY_KEY_PREFIX",
            defaults.conversation_memory_key_prefix,
        ),
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

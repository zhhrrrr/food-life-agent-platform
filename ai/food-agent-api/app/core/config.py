import os
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel, Field


class Settings(BaseModel):
    app_name: str = "Food Life Agent API"
    app_version: str = "0.27.0"
    app_port: int = 8500
    log_level: str = "INFO"
    readiness_runtime_checks_enabled: bool = True
    readiness_timeout_seconds: float = Field(default=2.0, gt=0, le=10.0)
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
    semantic_router_high_threshold: float = Field(default=0.74, ge=0.0, le=1.0)
    semantic_router_min_margin: float = Field(default=0.05, ge=0.0, le=1.0)
    semantic_router_failure_cooldown_seconds: float = Field(default=30.0, ge=1.0, le=300.0)
    orchestration_max_tasks: int = Field(default=12, ge=1, le=50)
    orchestration_max_depth: int = Field(default=6, ge=1, le=20)
    orchestration_max_parallel_tasks: int = Field(default=3, ge=1, le=10)
    orchestration_max_replans: int = Field(default=1, ge=0, le=3)
    orchestration_planner_timeout_seconds: float = Field(default=60.0, gt=0, le=180.0)
    mcp_enabled: bool = False
    mcp_mount_path: str = Field(default="/mcp", pattern=r"^/[A-Za-z0-9_-]+$")
    mcp_internal_token: str = ""
    mcp_allowed_hosts: list[str] = Field(default_factory=list)
    mcp_allowed_origins: list[str] = Field(default_factory=list)
    gateway_trusted_secret_header: str = "x-internal-secret"
    gateway_trusted_secret: str = ""
    agent_admin_token: str = ""
    session_memory_enabled: bool = False
    session_memory_ttl_seconds: int = Field(default=7200, ge=60)
    session_memory_max_turns: int = Field(default=8, ge=1, le=50)
    session_memory_key_prefix: str = "food-agent-api:session:"
    session_summary_enabled: bool = True
    session_summary_trigger_turns: int = Field(default=8, ge=2, le=50)
    session_summary_trigger_chars: int = Field(default=3000, ge=500, le=20000)
    memory_java_timeout_seconds: float = Field(default=3.0, gt=0, le=30)
    long_term_memory_enabled: bool = False
    long_term_memory_top_k: int = Field(default=3, ge=1, le=10)
    long_term_memory_candidate_k: int = Field(default=10, ge=1, le=50)
    long_term_memory_min_score: float = Field(default=0.55, ge=-1.0, le=1.0)
    long_term_memory_collection: str = "food_user_memory"
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
    es_number_of_shards: int = Field(default=1, ge=1)
    es_number_of_replicas: int = Field(default=0, ge=0)
    es_bulk_chunk_size: int = Field(default=200, ge=1, le=1000)
    es_rebuild_keep_versions: int = Field(default=2, ge=1, le=10)
    es_search_min_score: float = Field(default=0.1, ge=0.0)
    milvus_enabled: bool = False
    milvus_uri: str = "http://127.0.0.1:19530"
    milvus_token: str = ""
    milvus_database: str = "default"
    milvus_collection_prefix: str = Field(
        default="food",
        pattern=r"^[A-Za-z_][A-Za-z0-9_]*$",
    )
    milvus_index_type: str = "AUTOINDEX"
    milvus_metric_type: str = "COSINE"
    milvus_consistency_level: str = "Strong"
    milvus_timeout_seconds: float = Field(default=10.0, gt=0)
    milvus_batch_size: int = Field(default=200, ge=1, le=1000)
    milvus_flush_after_write: bool = True
    milvus_rebuild_keep_versions: int = Field(default=2, ge=1, le=10)
    rag_context_max_chars: int = Field(default=8000, ge=1000, le=30000)
    rag_hybrid_enabled: bool = False
    rag_embedding_dimensions: int = Field(default=1024, ge=1, le=4096)
    rag_embedding_batch_size: int = Field(default=32, ge=1, le=128)
    rag_candidate_size: int = Field(default=25, ge=1, le=100)
    rag_vector_min_score: float = Field(default=0.2, ge=-1.0, le=1.0)
    rag_rrf_k: int = Field(default=60, ge=1, le=1000)
    rag_rerank_enabled: bool = False
    rag_rerank_model: str = "BAAI/bge-reranker-v2-m3"
    rag_rerank_base_url: str = ""
    rag_rerank_api_key: str = ""
    rag_rerank_timeout_seconds: float = Field(default=10.0, gt=0)
    rag_rerank_min_score: float = Field(default=0.2, ge=0.0, le=1.0)
    rabbitmq_url: str = "amqp://guest:guest@127.0.0.1:5672/%2F"
    redis_url: str = "redis://127.0.0.1:6379/0"
    memory_sync_consumer_enabled: bool = False
    memory_sync_queue: str = "food.agent.memory.sync.queue"
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


def _get_csv(dotenv_values: dict[str, str], key: str) -> list[str]:
    return [
        item.strip()
        for item in _get_value(dotenv_values, key, "").split(",")
        if item.strip()
    ]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    dotenv_values = _read_dotenv(Path.cwd() / ".env")
    defaults = Settings()

    return Settings(
        app_name=_get_value(dotenv_values, "APP_NAME", defaults.app_name),
        app_version=_get_value(dotenv_values, "APP_VERSION", defaults.app_version),
        app_port=int(_get_value(dotenv_values, "APP_PORT", str(defaults.app_port))),
        log_level=_get_value(dotenv_values, "LOG_LEVEL", defaults.log_level),
        readiness_runtime_checks_enabled=_get_value(
            dotenv_values,
            "READINESS_RUNTIME_CHECKS_ENABLED",
            str(defaults.readiness_runtime_checks_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        readiness_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "READINESS_TIMEOUT_SECONDS",
                str(defaults.readiness_timeout_seconds),
            )
        ),
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
        orchestration_max_tasks=int(
            _get_value(
                dotenv_values,
                "ORCHESTRATION_MAX_TASKS",
                str(defaults.orchestration_max_tasks),
            )
        ),
        orchestration_max_depth=int(
            _get_value(
                dotenv_values,
                "ORCHESTRATION_MAX_DEPTH",
                str(defaults.orchestration_max_depth),
            )
        ),
        orchestration_max_parallel_tasks=int(
            _get_value(
                dotenv_values,
                "ORCHESTRATION_MAX_PARALLEL_TASKS",
                str(defaults.orchestration_max_parallel_tasks),
            )
        ),
        orchestration_max_replans=int(
            _get_value(
                dotenv_values,
                "ORCHESTRATION_MAX_REPLANS",
                str(defaults.orchestration_max_replans),
            )
        ),
        orchestration_planner_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "ORCHESTRATION_PLANNER_TIMEOUT_SECONDS",
                str(defaults.orchestration_planner_timeout_seconds),
            )
        ),
        mcp_enabled=_get_value(
            dotenv_values,
            "MCP_ENABLED",
            str(defaults.mcp_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        mcp_mount_path=_get_value(
            dotenv_values,
            "MCP_MOUNT_PATH",
            defaults.mcp_mount_path,
        ),
        mcp_internal_token=_get_value(
            dotenv_values,
            "MCP_INTERNAL_TOKEN",
            defaults.mcp_internal_token,
        ),
        mcp_allowed_hosts=_get_csv(dotenv_values, "MCP_ALLOWED_HOSTS"),
        mcp_allowed_origins=_get_csv(dotenv_values, "MCP_ALLOWED_ORIGINS"),
        gateway_trusted_secret_header=_get_value(
            dotenv_values,
            "GATEWAY_TRUSTED_SECRET_HEADER",
            defaults.gateway_trusted_secret_header,
        ).lower(),
        gateway_trusted_secret=_get_value(
            dotenv_values,
            "GATEWAY_TRUSTED_SECRET",
            defaults.gateway_trusted_secret,
        ),
        agent_admin_token=_get_value(
            dotenv_values,
            "AGENT_ADMIN_TOKEN",
            defaults.agent_admin_token,
        ),
        session_memory_enabled=_get_value(
            dotenv_values,
            "SESSION_MEMORY_ENABLED",
            str(defaults.session_memory_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        session_memory_ttl_seconds=int(
            _get_value(
                dotenv_values,
                "SESSION_MEMORY_TTL_SECONDS",
                str(defaults.session_memory_ttl_seconds),
            )
        ),
        session_memory_max_turns=int(
            _get_value(
                dotenv_values,
                "SESSION_MEMORY_MAX_TURNS",
                str(defaults.session_memory_max_turns),
            )
        ),
        session_memory_key_prefix=_get_value(
            dotenv_values,
            "SESSION_MEMORY_KEY_PREFIX",
            defaults.session_memory_key_prefix,
        ),
        session_summary_enabled=_get_value(
            dotenv_values, "SESSION_SUMMARY_ENABLED", str(defaults.session_summary_enabled)
        ).lower() in {"1", "true", "yes", "on"},
        session_summary_trigger_turns=int(_get_value(
            dotenv_values, "SESSION_SUMMARY_TRIGGER_TURNS", str(defaults.session_summary_trigger_turns)
        )),
        session_summary_trigger_chars=int(_get_value(
            dotenv_values, "SESSION_SUMMARY_TRIGGER_CHARS", str(defaults.session_summary_trigger_chars)
        )),
        memory_java_timeout_seconds=float(_get_value(
            dotenv_values, "MEMORY_JAVA_TIMEOUT_SECONDS", str(defaults.memory_java_timeout_seconds)
        )),
        long_term_memory_enabled=_get_value(
            dotenv_values, "LONG_TERM_MEMORY_ENABLED", str(defaults.long_term_memory_enabled)
        ).lower() in {"1", "true", "yes", "on"},
        long_term_memory_top_k=int(_get_value(
            dotenv_values, "LONG_TERM_MEMORY_TOP_K", str(defaults.long_term_memory_top_k)
        )),
        long_term_memory_candidate_k=int(_get_value(
            dotenv_values, "LONG_TERM_MEMORY_CANDIDATE_K", str(defaults.long_term_memory_candidate_k)
        )),
        long_term_memory_min_score=float(_get_value(
            dotenv_values, "LONG_TERM_MEMORY_MIN_SCORE", str(defaults.long_term_memory_min_score)
        )),
        long_term_memory_collection=_get_value(
            dotenv_values, "LONG_TERM_MEMORY_COLLECTION", defaults.long_term_memory_collection
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
        es_number_of_shards=int(
            _get_value(
                dotenv_values,
                "ES_NUMBER_OF_SHARDS",
                str(defaults.es_number_of_shards),
            )
        ),
        es_number_of_replicas=int(
            _get_value(
                dotenv_values,
                "ES_NUMBER_OF_REPLICAS",
                str(defaults.es_number_of_replicas),
            )
        ),
        es_bulk_chunk_size=int(
            _get_value(
                dotenv_values,
                "ES_BULK_CHUNK_SIZE",
                str(defaults.es_bulk_chunk_size),
            )
        ),
        es_rebuild_keep_versions=int(
            _get_value(
                dotenv_values,
                "ES_REBUILD_KEEP_VERSIONS",
                str(defaults.es_rebuild_keep_versions),
            )
        ),
        es_search_min_score=float(
            _get_value(
                dotenv_values,
                "ES_SEARCH_MIN_SCORE",
                str(defaults.es_search_min_score),
            )
        ),
        milvus_enabled=_get_value(
            dotenv_values,
            "MILVUS_ENABLED",
            str(defaults.milvus_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        milvus_uri=_get_value(dotenv_values, "MILVUS_URI", defaults.milvus_uri),
        milvus_token=_get_value(dotenv_values, "MILVUS_TOKEN", defaults.milvus_token),
        milvus_database=_get_value(
            dotenv_values,
            "MILVUS_DATABASE",
            defaults.milvus_database,
        ),
        milvus_collection_prefix=_get_value(
            dotenv_values,
            "MILVUS_COLLECTION_PREFIX",
            defaults.milvus_collection_prefix,
        ),
        milvus_index_type=_get_value(
            dotenv_values,
            "MILVUS_INDEX_TYPE",
            defaults.milvus_index_type,
        ),
        milvus_metric_type=_get_value(
            dotenv_values,
            "MILVUS_METRIC_TYPE",
            defaults.milvus_metric_type,
        ),
        milvus_consistency_level=_get_value(
            dotenv_values,
            "MILVUS_CONSISTENCY_LEVEL",
            defaults.milvus_consistency_level,
        ),
        milvus_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "MILVUS_TIMEOUT_SECONDS",
                str(defaults.milvus_timeout_seconds),
            )
        ),
        milvus_batch_size=int(
            _get_value(
                dotenv_values,
                "MILVUS_BATCH_SIZE",
                str(defaults.milvus_batch_size),
            )
        ),
        milvus_flush_after_write=_get_value(
            dotenv_values,
            "MILVUS_FLUSH_AFTER_WRITE",
            str(defaults.milvus_flush_after_write),
        ).lower()
        in {"1", "true", "yes", "on"},
        milvus_rebuild_keep_versions=int(
            _get_value(
                dotenv_values,
                "MILVUS_REBUILD_KEEP_VERSIONS",
                str(defaults.milvus_rebuild_keep_versions),
            )
        ),
        rag_context_max_chars=int(
            _get_value(
                dotenv_values,
                "RAG_CONTEXT_MAX_CHARS",
                str(defaults.rag_context_max_chars),
            )
        ),
        rag_hybrid_enabled=_get_value(
            dotenv_values,
            "RAG_HYBRID_ENABLED",
            str(defaults.rag_hybrid_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        rag_embedding_dimensions=int(
            _get_value(
                dotenv_values,
                "RAG_EMBEDDING_DIMENSIONS",
                str(defaults.rag_embedding_dimensions),
            )
        ),
        rag_embedding_batch_size=int(
            _get_value(
                dotenv_values,
                "RAG_EMBEDDING_BATCH_SIZE",
                str(defaults.rag_embedding_batch_size),
            )
        ),
        rag_candidate_size=int(
            _get_value(
                dotenv_values,
                "RAG_CANDIDATE_SIZE",
                str(defaults.rag_candidate_size),
            )
        ),
        rag_vector_min_score=float(
            _get_value(
                dotenv_values,
                "RAG_VECTOR_MIN_SCORE",
                str(defaults.rag_vector_min_score),
            )
        ),
        rag_rrf_k=int(
            _get_value(dotenv_values, "RAG_RRF_K", str(defaults.rag_rrf_k))
        ),
        rag_rerank_enabled=_get_value(
            dotenv_values,
            "RAG_RERANK_ENABLED",
            str(defaults.rag_rerank_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        rag_rerank_model=_get_value(
            dotenv_values,
            "RAG_RERANK_MODEL",
            defaults.rag_rerank_model,
        ),
        rag_rerank_base_url=_get_value(
            dotenv_values,
            "RAG_RERANK_BASE_URL",
            defaults.rag_rerank_base_url,
        ),
        rag_rerank_api_key=_get_value(
            dotenv_values,
            "RAG_RERANK_API_KEY",
            defaults.rag_rerank_api_key,
        ),
        rag_rerank_timeout_seconds=float(
            _get_value(
                dotenv_values,
                "RAG_RERANK_TIMEOUT_SECONDS",
                str(defaults.rag_rerank_timeout_seconds),
            )
        ),
        rag_rerank_min_score=float(
            _get_value(
                dotenv_values,
                "RAG_RERANK_MIN_SCORE",
                str(defaults.rag_rerank_min_score),
            )
        ),
        rabbitmq_url=_get_value(dotenv_values, "RABBITMQ_URL", defaults.rabbitmq_url),
        redis_url=_get_value(dotenv_values, "REDIS_URL", defaults.redis_url),
        memory_sync_consumer_enabled=_get_value(
            dotenv_values, "MEMORY_SYNC_CONSUMER_ENABLED", str(defaults.memory_sync_consumer_enabled)
        ).lower() in {"1", "true", "yes", "on"},
        memory_sync_queue=_get_value(
            dotenv_values, "MEMORY_SYNC_QUEUE", defaults.memory_sync_queue
        ),
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
    )

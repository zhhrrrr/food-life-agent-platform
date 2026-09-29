import os

os.environ.update(
    {
        "APP_VERSION": "0.25.0",
        "LLM_ROUTER_ENABLED": "false",
        "SEMANTIC_ROUTER_ENABLED": "false",
        "ES_ENABLED": "false",
        "MILVUS_ENABLED": "false",
        "RAG_HYBRID_ENABLED": "false",
        "RAG_RERANK_ENABLED": "false",
        "SESSION_MEMORY_ENABLED": "false",
        "LONG_TERM_MEMORY_ENABLED": "false",
        "ES_SYNC_CONSUMER_ENABLED": "false",
        "ES_RECONCILE_ENABLED": "false",
        "MEMORY_SYNC_CONSUMER_ENABLED": "false",
        "MCP_ENABLED": "false",
        "GATEWAY_TRUSTED_SECRET": "test-gateway-secret",
        "AGENT_ADMIN_TOKEN": "test-admin-token",
    }
)

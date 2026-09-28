# Food Agent API

Food Life Agent / food-agent-api Python Agent service.

Current version: `0.19.2`

Current route chain:

```text
HTTP Request
-> FastAPI
-> Redis Conversation Memory
-> LangGraph State
-> Semantic Router (Embedding cosine similarity + confidence/margin gates)
   -> high confidence: direct agent route
   -> low confidence/unavailable: LLM Router Function Calling
      -> Structured Output compatibility fallback
      -> deterministic rule fallback
-> Recommend / Trade / Support Agent
-> Recommend query rewrite -> Embedding + ES BM25 / Milvus vector retrieval -> RRF -> Rerank -> bounded Top-K context -> Prompt + Query -> LLM answer
-> Support query rewrite -> Embedding + ES rule BM25 / Milvus vector retrieval -> RRF -> Rerank -> bounded Top-K context -> Prompt + Query -> LLM answer
-> Trade query rewrite -> allow-listed Java Gateway GET queries -> LLM answer
-> Response
```

Added by version:

```text
0.19.2: local Milvus Lite development runtime with a production Milvus-compatible configuration boundary
0.19.1: retrieval cleanup, evidence/reference consistency, and relevance floors
0.19.0: independent Milvus vector store with dual-store rebuild, sync, and reconciliation
0.18.0: BGE-M3 vector retrieval, BM25 hybrid search, RRF fusion, and BGE reranking
0.17.0: production RAG index lifecycle, scoped retrieval, filtering, context, and citation validation
0.16.1: hardened direct Java HTTP calls and ES text-field retrieval
0.16.0: narrowed ES documents to recommendation fields and removed stock-only sync work
0.15.0: removed duplicate Support order reads and shared repeated chain fallback logic
0.14.0: production semantic-route corpus, independent evaluation set, and BGE-M3 input compatibility
0.1.0: FastAPI + LangGraph skeleton
0.2.0: settings, request_id, logging, stable error response
0.3.0: optional Elasticsearch search layer + lightweight RAG retrieval path
0.4.0: Java search export client + manual ES rebuild endpoint
0.5.0: RabbitMQ incremental ES sync consumer + single-document upsert/delete
0.6.0: scheduled/manual ES reconciliation for missed events and stale documents
0.6.1: single-instance reconciliation scheduler hardening
0.6.2: Redis distributed lock for multi-worker reconciliation safety
0.6.3: extracted Redis distributed lock into a reusable sync module
0.7.0: stage 4 RAG retrieval chain with ES BM25 context and template answers
0.7.1: simplified ES reconciliation locking to a single Redis distributed lock
0.8.0: seeded platform rule and FAQ-style documents into the ES rule indexing path
0.9.0: LangChain ChatModel router with Pydantic Structured Output and rule fallback
0.9.1: Zhipu GLM OpenAI-compatible chat model configuration
0.9.2: Zhipu router structured output compatibility through JSON mode
0.13.2: removed obsolete compatibility wrappers, empty graph state, and unused dependencies
0.13.1: SiliconFlow BGE-M3 embedding provider and calibrated semantic routing gates
0.13.0: Redis conversation memory and Semantic Router + LLM Function Calling routing pipeline
0.12.0: stage 8 read-only TradeAgent queries through Java Gateway and unified three-chain contracts
0.11.1: unified RecommendAgent and SupportAgent chain contracts, validation, fallbacks, and structured output
0.11.0: stage 7 SupportAgent rule RAG, read-only Java order-status query, and LLM answer chain
0.10.0: stage 6 RecommendAgent query rewrite, ES/RAG retrieval, and LLM answer chain
```

This version does not connect to MySQL directly. Java remains the source of truth for MySQL data and exposes read-only search document endpoints; Python writes text projections to Elasticsearch and embeddings to independent Milvus collections. LangChain provides model integration while LangGraph orchestrates the agent flow. RecommendAgent and SupportAgent run query rewrite -> Elasticsearch BM25 plus Milvus vector retrieval -> reciprocal rank fusion -> BGE reranking -> bounded context -> LLM answer -> validated references. TradeAgent exclusively owns current order, price, stock, coupon, group-buy, and preview reads through allow-listed Java Gateway GET queries. Search projections remain focused on recommendation discovery and exclude current stock and coupon supply counters. MCP, SSE, Human Confirmation, and business action tools are still future stages.

Elasticsearch and Milvus support are disabled by default. Enable each store only after its service is healthy; hybrid retrieval requires both `RAG_HYBRID_ENABLED=true` and `MILVUS_ENABLED=true`.

Run the deterministic retrieval quality gate after rebuilding the indexes:

```bash
python scripts/evaluate_rag.py --min-recall 0.9
```

## Requirements

- Python 3.11+

## Create Virtual Environment

```bash
python -m venv .venv
```

Windows PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
```

macOS / Linux:

```bash
source .venv/bin/activate
```

## Install Dependencies

```bash
pip install -r requirements.txt
```

## Configuration

Copy `.env.example` to `.env`, or provide environment variables directly:

```text
APP_NAME=food-agent-api
APP_VERSION=0.19.2
APP_PORT=8500
LOG_LEVEL=INFO
LLM_ROUTER_ENABLED=false
LLM_PROVIDER=openai
LLM_MODEL=gpt-4o-mini
LLM_API_KEY=
LLM_BASE_URL=
LLM_TEMPERATURE=0
LLM_TIMEOUT_SECONDS=10
SEMANTIC_ROUTER_ENABLED=false
EMBEDDING_PROVIDER=openai
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_API_KEY=
EMBEDDING_BASE_URL=
EMBEDDING_TIMEOUT_SECONDS=10
SEMANTIC_ROUTER_HIGH_THRESHOLD=0.74
SEMANTIC_ROUTER_MIN_MARGIN=0.05
SEMANTIC_ROUTER_FAILURE_COOLDOWN_SECONDS=30
CONVERSATION_MEMORY_ENABLED=false
CONVERSATION_MEMORY_TTL_SECONDS=86400
CONVERSATION_MEMORY_MAX_TURNS=8
CONVERSATION_MEMORY_KEY_PREFIX=food-agent-api:conversation:
JAVA_GATEWAY_BASE_URL=http://127.0.0.1:8080
JAVA_BUSINESS_BASE_URL=http://127.0.0.1:8201
JAVA_TRADE_BASE_URL=http://127.0.0.1:8301
ES_ENABLED=false
ES_URL=http://127.0.0.1:9200
ES_INDEX_SHOP=food_shop_index
ES_INDEX_PACKAGE=food_package_index
ES_INDEX_RULE=food_rule_index
ES_INDEX_REVIEW=food_review_index
ES_INDEX_ACTIVITY=food_activity_index
ES_INDEX_COUPON=food_coupon_index
ES_NUMBER_OF_SHARDS=1
ES_NUMBER_OF_REPLICAS=0
ES_BULK_CHUNK_SIZE=200
ES_REBUILD_KEEP_VERSIONS=2
ES_SEARCH_MIN_SCORE=0.1
MILVUS_ENABLED=false
MILVUS_URI=http://127.0.0.1:19530
MILVUS_TOKEN=
MILVUS_DATABASE=default
MILVUS_COLLECTION_PREFIX=food
MILVUS_INDEX_TYPE=AUTOINDEX
MILVUS_METRIC_TYPE=COSINE
MILVUS_CONSISTENCY_LEVEL=Strong
MILVUS_TIMEOUT_SECONDS=10
MILVUS_BATCH_SIZE=200
MILVUS_FLUSH_AFTER_WRITE=true
MILVUS_REBUILD_KEEP_VERSIONS=2
RAG_CONTEXT_MAX_CHARS=8000
RAG_HYBRID_ENABLED=false
RAG_EMBEDDING_DIMENSIONS=1024
RAG_EMBEDDING_BATCH_SIZE=32
RAG_CANDIDATE_SIZE=25
RAG_VECTOR_MIN_SCORE=0.2
RAG_RRF_K=60
RAG_RERANK_ENABLED=false
RAG_RERANK_MODEL=BAAI/bge-reranker-v2-m3
RAG_RERANK_BASE_URL=https://api.siliconflow.cn/v1
RAG_RERANK_API_KEY=
RAG_RERANK_TIMEOUT_SECONDS=10
RAG_RERANK_MIN_SCORE=0.1
RABBITMQ_URL=amqp://guest:guest@127.0.0.1:5672/%2F
REDIS_URL=redis://127.0.0.1:6379/0
ES_SYNC_CONSUMER_ENABLED=false
ES_SYNC_BUSINESS_QUEUE=food.business.search.sync.queue
ES_SYNC_TRADE_QUEUE=food.trade.search.sync.queue
ES_RECONCILE_ENABLED=false
ES_RECONCILE_INTERVAL_SECONDS=900
ES_RECONCILE_RUN_ON_STARTUP=true
ES_RECONCILE_DISTRIBUTED_LOCK_ENABLED=false
ES_RECONCILE_LOCK_KEY=food-agent-api:es-reconciliation:lock
ES_RECONCILE_LOCK_TTL_SECONDS=1800
ES_RECONCILE_PAGE_SIZE=200
ES_RECONCILE_PRUNE_STALE=true
```

Zhipu GLM router example:

```text
LLM_ROUTER_ENABLED=true
LLM_PROVIDER=zhipu
LLM_MODEL=glm-4.5-air
LLM_API_KEY=your-zhipu-api-key
LLM_BASE_URL=https://api.z.ai/api/paas/v4/
```

## LLM Router

The LLM router is disabled by default. When `LLM_ROUTER_ENABLED=true` and `LLM_API_KEY` is configured, LangChain calls the chat model with Pydantic Structured Output:

```python
class IntentResult(BaseModel):
    intent: Literal["recommend", "trade", "support"]
    confidence: float
    reason: str
```

The default provider is `openai`. Set `LLM_PROVIDER=zhipu` to call Zhipu GLM through its OpenAI-compatible API. If `LLM_BASE_URL` is empty for `zhipu`, the service uses `https://api.z.ai/api/paas/v4/`. Zhipu routing uses LangChain structured output with JSON mode so OpenAI-compatible responses can be parsed into `IntentResult`.

If the LLM provider is not configured, unavailable, or returns an invalid structure, the router falls back to the existing keyword rules.

## Semantic Router And Memory

The routing order is:

```text
current query + relevant Redis conversation turns
-> embedding similarity against versioned route examples
-> accept only when score >= high threshold and top-intent margin >= minimum margin
-> otherwise LLM Function Calling selects route_recommend / route_trade / route_support
-> Structured Output compatibility fallback
-> deterministic rule fallback
```

Semantic routing and Redis memory are disabled until their providers are configured. Embeddings use a separate API key from the chat model. OpenAI, Zhipu, SiliconFlow, and custom OpenAI-compatible endpoints are supported. Conversation history participates in deterministic and semantic routing only for follow-up wording such as “那这个呢” or “还有吗”, which prevents an old topic from overriding an explicit new request. Embedding and Redis failures enter a short per-instance cooldown and fail open to the next routing layer instead of delaying every request.

The semantic corpus contains 138 validated, capability-labelled route anchors. The independent 60-case evaluation set covers balanced intents, difficult intent boundaries, and contextual follow-ups. Run the live quality gate with the configured embedding provider:

```powershell
.\.venv\Scripts\python.exe -m scripts.evaluate_semantic_router
```

The evaluator fails when top-1 accuracy is below 90%, when the configured acceptance gate has no coverage, or when accepted-route precision is below 98%. Threshold changes must be justified by this independent set and later rechecked against anonymized production traffic.

SiliconFlow example:

```text
SEMANTIC_ROUTER_ENABLED=true
EMBEDDING_PROVIDER=siliconflow
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_API_KEY=your-siliconflow-key
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
SEMANTIC_ROUTER_HIGH_THRESHOLD=0.74
SEMANTIC_ROUTER_MIN_MARGIN=0.05
```

The three routing tools only select a LangGraph agent. They do not expose Java, order, payment, refund, or inventory write operations. Redis stores bounded, expiring `user`, `assistant`, and `intent` turns; Authorization is never stored.

Each chat response returns `conversation_id`, `router_source`, and `router_confidence`. Send the same `conversation_id` in the next request to continue the conversation.

## Start Service

```bash
uvicorn app.main:app --reload --port 8500
```

## Test API

Health:

```bash
curl http://127.0.0.1:8500/health
```

Recommend:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"帮我推荐一家川菜馆\"}"
```

Trade:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"帮我查询一下这个订单\"}"
```

Support:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"退款规则是什么\"}"
```

Rebuild Elasticsearch indexes and Milvus collections from Java exports:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/indexes/rebuild \
  -H "Content-Type: application/json" \
  -d "{\"scopes\":[\"shops\",\"packages\",\"reviews\",\"activities\",\"coupons\"],\"page_size\":200}"
```

Reconcile Elasticsearch indexes and Milvus collections against Java current-state exports:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/indexes/reconcile \
  -H "Content-Type: application/json" \
  -d "{\"scopes\":[\"shops\",\"packages\",\"reviews\",\"activities\",\"coupons\"],\"page_size\":200,\"prune_stale\":true}"
```

Rebuild seeded platform rule documents for SupportAgent:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/indexes/rebuild \
  -H "Content-Type: application/json" \
  -d "{\"scopes\":[\"rules\"]}"
```

Rules are not MySQL business rows. They use the same `SearchDocument` projection but come from the local platform rule document source until formal operations Markdown/config imports are added.

Start the independent Milvus Standalone service from the repository root:

```bash
docker compose -f deploy/milvus/docker-compose.yml up -d
```

Milvus exposes its client endpoint on `127.0.0.1:19530` and health endpoint on `127.0.0.1:9091`. After the service is healthy, set `MILVUS_ENABLED=true`, restart the API, and run a full rebuild once to create and populate all vector collection aliases.

For local Windows development without Docker, install the separate local profile and run Milvus Lite as an independent gRPC process:

```powershell
python -m pip install -r requirements-local.txt
milvus-lite server --data-dir .runtime/milvus-lite-data --host 127.0.0.1 --port 19530
```

Keep `MILVUS_URI=http://127.0.0.1:19530`, set `MILVUS_ENABLED=true`, and set `MILVUS_FLUSH_AFTER_WRITE=false` for Milvus Lite because its Windows gRPC server does not reliably implement the blocking flush-state polling used by `pymilvus`. Milvus Lite persists inserts through its WAL. Milvus Lite is only the local development and retrieval-evaluation runtime. On the server, point the same `MILVUS_URI`, token, and database settings at Milvus Standalone, Milvus Distributed, or Zilliz Cloud and restore `MILVUS_FLUSH_AFTER_WRITE=true`; application retrieval and indexing code does not change.

Incremental search sync is disabled by default. Enable it only after RabbitMQ, Elasticsearch, Milvus, and Java services are available:

```text
ES_ENABLED=true
MILVUS_ENABLED=true
ES_SYNC_CONSUMER_ENABLED=true
ES_RECONCILE_ENABLED=true
ES_RECONCILE_DISTRIBUTED_LOCK_ENABLED=true
```

Scheduled search reconciliation uses one timer thread per process and one Redis distributed lock per run. The flow is: timer waits for the interval, calls `_run_once()`, tries to acquire the Redis lock, skips the round if the lock is busy, runs `reconcile()` only after lock success, and releases the lock in `finally`. Reconciliation compares the Java/MySQL current-state projection with Elasticsearch content and a Milvus content hash, then repairs missing, stale, or extra records in both stores.

When retrieval returns matches, `POST /api/agent/chat` also includes:

```json
{
  "rag_mode": "recommend_agent_hybrid_rrf_rerank_llm",
  "references": [
    {
      "id": "shop-1",
      "title": "Example Shop",
      "source": "food_shop_index",
      "score": 1.0
    }
  ]
}
```

RecommendAgent searches shop, package, review, activity, and coupon projections. They contain public descriptions, prices, scopes, validity windows, and popularity data, but no package/activity stock or coupon supply counters. RecommendAgent can rewrite questions for shop/package recommendation, cuisine filtering, budget filtering, scene recommendation, review semantic search, and shop comparison before retrieval. It must not claim current availability from search projections. SupportAgent searches only the platform rule projection for refund, group-buy, payment, generic order-status meanings, and platform FAQ; it does not receive Authorization or query Java order APIs. If no evidence matches, these agents return a clear no-evidence template instead of pretending to have generated an answer.

When both `RAG_HYBRID_ENABLED=true` and `MILVUS_ENABLED=true`, every document receives an embedding generated from its title, category, and content and stored only in Milvus. Each query retrieves BM25 candidates from Elasticsearch and cosine-similarity candidates from Milvus. RRF runs only when both channels return candidates; a single healthy channel keeps its native score. Candidates below `RAG_VECTOR_MIN_SCORE` are discarded, fused candidates are optionally reranked, and results below `RAG_RERANK_MIN_SCORE` are discarded. Prompt context and returned references are built from the same bounded evidence set. If query embedding, Milvus retrieval, or reranking fails, retrieval degrades to the available channel. Changing the embedding model or dimensions requires a full rebuild of the versioned Milvus collections.

Production rollout is complete only after Milvus is running, all collection aliases have passed a full rebuild, hybrid retrieval evaluation passes on representative queries, score floors are calibrated from those results, and authentication plus backups are configured for Elasticsearch and Milvus.

TradeAgent queries current price, stock, coupons, group-buy teams/orders, user orders, and read-only order previews through `JAVA_GATEWAY_BASE_URL`. Its Java client exposes only fixed GET methods and forwards the incoming Authorization header for protected user queries. Order preview composes the package trade snapshot, a pre-discount estimate in cents, and candidate user coupons without claiming a final payable amount, creating an order, or reserving stock. Create, cancel, pay, and refund requests are rejected before any Gateway call.

## Run Tests

```bash
pytest
```

## Next Step

Replace seeded platform rules with formal operations Markdown/config imports when the production rule documents are available.

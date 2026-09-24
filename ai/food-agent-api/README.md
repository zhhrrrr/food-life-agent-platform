# Food Agent API

Food Life Agent / food-agent-api Python Agent service.

Current version: `0.12.0`

Current route chain:

```text
HTTP Request
-> FastAPI
-> LangGraph State
-> LLM Router with Pydantic Structured Output
-> Recommend / Trade / Support Agent
-> Recommend query rewrite -> ES BM25 retrieval -> LLM answer
-> Support query rewrite -> rule retrieval -> optional Java order-status query -> LLM answer
-> Trade query rewrite -> allow-listed Java Gateway GET queries -> LLM answer
-> Response
```

Added by version:

```text
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
0.12.0: stage 8 read-only TradeAgent queries through Java Gateway and unified three-chain contracts
0.11.1: unified RecommendAgent and SupportAgent chain contracts, validation, fallbacks, and structured output
0.11.0: stage 7 SupportAgent rule RAG, read-only Java order-status query, and LLM answer chain
0.10.0: stage 6 RecommendAgent query rewrite, ES/RAG retrieval, and LLM answer chain
```

This version does not connect to MySQL directly. Java remains the source of truth for MySQL data and exposes read-only search document endpoints; Python pulls those endpoints and writes Elasticsearch. Stage 4 is a lightweight RAG retrieval chain. Stage 5 uses LangChain for LLM intent routing while LangGraph continues to orchestrate the agent flow. Stage 6 upgrades RecommendAgent to query rewrite -> ES retrieval -> context builder -> LLM answer -> references. Stage 7 upgrades SupportAgent to query rewrite -> rule retrieval -> optional owner-authorized Java order-status query -> LLM answer -> references. Stage 8 upgrades TradeAgent to query rewrite -> allow-listed Java Gateway GET queries -> context -> LLM answer -> references. All three chains share the same structured-output invocation, non-empty query/capability validation, local capability completion, result contract, and reference selection rules. Tool Calling, MCP, Redis Memory, SSE, and Human Confirmation are still future stages.

Elasticsearch support is present but disabled by default. Set `ES_ENABLED=true` only after Elasticsearch is available and the indexes have data.

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
APP_VERSION=0.12.0
APP_PORT=8500
LOG_LEVEL=INFO
LLM_ROUTER_ENABLED=false
LLM_PROVIDER=openai
LLM_MODEL=gpt-4o-mini
LLM_API_KEY=
LLM_BASE_URL=
LLM_TEMPERATURE=0
LLM_TIMEOUT_SECONDS=10
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
ES_RECONCILE_SCAN_SIZE=10000
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

Rebuild ES indexes from Java exports:

```bash
curl -X POST http://127.0.0.1:8500/api/agent/indexes/rebuild \
  -H "Content-Type: application/json" \
  -d "{\"scopes\":[\"shops\",\"packages\",\"reviews\",\"activities\",\"coupons\"],\"limit\":200}"
```

Reconcile ES indexes against Java current-state exports:

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

Rules are not MySQL business rows. They use the same `SearchDocument` ES schema but come from the local platform rule document source until formal operations Markdown/config imports are added.

Incremental ES sync is disabled by default. Enable it only after RabbitMQ, Elasticsearch, and Java services are available:

```text
ES_ENABLED=true
ES_SYNC_CONSUMER_ENABLED=true
ES_RECONCILE_ENABLED=true
ES_RECONCILE_DISTRIBUTED_LOCK_ENABLED=true
```

Scheduled ES reconciliation uses one timer thread per process and one Redis distributed lock per run. The flow is: timer waits for the interval, calls `_run_once()`, tries to acquire the Redis lock, skips the round if the lock is busy, runs `reconcile()` only after lock success, and releases the lock in `finally`.

When ES returns matches, `POST /api/agent/chat` also includes:

```json
{
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

RecommendAgent searches shop, package, review, activity, and coupon indexes. It can rewrite recommendation questions for shop/package recommendation, cuisine filtering, budget filtering, scene recommendation, review semantic search, and shop comparison before ES retrieval. When LLM is enabled, it generates an evidence-grounded answer from the retrieved context and keeps references. It does not create orders or change stock. SupportAgent searches the platform rule index for refund, group-buy, payment, order-status explanation, and platform FAQ. When a message explicitly contains an order ID and asks for its status, it forwards the incoming Authorization header to Java's owner-authorized GET /api/trade/orders/{orderId}; it never queries MySQL or uses an operations endpoint. If ES is disabled or no document matches, the agents return a clear no-evidence template instead of pretending to have generated an answer.

TradeAgent queries current price, stock, coupons, group-buy teams/orders, user orders, and read-only order previews through `JAVA_GATEWAY_BASE_URL`. Its Java client exposes only fixed GET methods and forwards the incoming Authorization header for protected user queries. Order preview composes the package trade snapshot, a pre-discount estimate in cents, and candidate user coupons without claiming a final payable amount, creating an order, or reserving stock. Create, cancel, pay, and refund requests are rejected before any Gateway call.

## Run Tests

```bash
pytest
```

## Next Step

Replace seeded platform rules with formal operations Markdown/config imports when the production rule documents are available.

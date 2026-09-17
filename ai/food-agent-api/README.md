# Food Agent API

Food Life Agent / food-agent-api Python Agent service.

Current version: `0.6.0`

Current route chain:

```text
HTTP Request
-> FastAPI
-> LangGraph State
-> rule-based Router
-> Recommend / Trade / Support Agent
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
```

This version does not connect to MySQL directly. Java remains the source of truth for MySQL data and exposes read-only search document endpoints; Python pulls those endpoints and writes Elasticsearch. LLM, Tool Calling, MCP, Redis Memory, SSE, and Human Confirmation are still future stages.

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
APP_VERSION=0.6.0
APP_PORT=8500
LOG_LEVEL=INFO
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
ES_SYNC_CONSUMER_ENABLED=false
ES_SYNC_BUSINESS_QUEUE=food.business.search.sync.queue
ES_SYNC_TRADE_QUEUE=food.trade.search.sync.queue
ES_RECONCILE_ENABLED=false
ES_RECONCILE_INTERVAL_SECONDS=900
ES_RECONCILE_PAGE_SIZE=200
ES_RECONCILE_PRUNE_STALE=true
ES_RECONCILE_SCAN_SIZE=10000
```

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

Incremental ES sync is disabled by default. Enable it only after RabbitMQ, Elasticsearch, and Java services are available:

```text
ES_ENABLED=true
ES_SYNC_CONSUMER_ENABLED=true
ES_RECONCILE_ENABLED=true
```

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

## Run Tests

```bash
pytest
```

## Next Step

Import platform rules and FAQ articles from formal Markdown or configuration documents, then add them to the support RAG indexes.

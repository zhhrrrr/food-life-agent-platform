# Food Agent API

Food Life Agent / food-agent-api Python Agent service.

Current version: `0.3.0`

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
```

This version does not connect to MySQL and does not call Java business services. LLM, Tool Calling, MCP, Redis Memory, SSE, and Human Confirmation are still future stages.

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
APP_VERSION=0.3.0
APP_PORT=8500
LOG_LEVEL=INFO
JAVA_GATEWAY_BASE_URL=http://127.0.0.1:8080
ES_ENABLED=false
ES_URL=http://127.0.0.1:9200
ES_INDEX_SHOP=food_shop_index
ES_INDEX_PACKAGE=food_package_index
ES_INDEX_RULE=food_rule_index
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

Expose Java read-only data export APIs and populate the ES indexes from Java-owned business facts.

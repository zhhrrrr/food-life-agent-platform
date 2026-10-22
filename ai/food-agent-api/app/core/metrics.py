from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.responses import Response

HTTP_REQUESTS = Counter(
    "food_agent_http_requests_total",
    "HTTP requests handled by Food Agent API.",
    ("method", "route", "status"),
)
HTTP_DURATION = Histogram(
    "food_agent_http_request_duration_seconds",
    "Food Agent API HTTP request duration.",
    ("method", "route"),
)
CHAT_REQUESTS = Counter(
    "food_agent_chat_requests_total",
    "Completed Food Agent chat requests.",
    ("intent", "execution_status"),
)
MEMORY_OPERATIONS = Counter("food_agent_memory_operations_total", "Memory delivery, summary and indexing outcomes.", ("operation", "status"))
TOOL_EXECUTIONS = Counter(
    "food_agent_tool_executions_total",
    "Native tool execution outcomes.",
    ("tool", "status"),
)
TOOL_DURATION = Histogram(
    "food_agent_tool_duration_seconds", "Tool execution including retries and capacity checks.",
    ("tool", "status"),
)
TOOL_RETRIES = Counter(
    "food_agent_tool_retries_total", "Bounded read tool retries.", ("tool", "reason"),
)


def metrics_response() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

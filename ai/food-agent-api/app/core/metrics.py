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
TOOL_EXECUTIONS = Counter(
    "food_agent_tool_executions_total",
    "Native tool execution outcomes.",
    ("tool", "status"),
)


def metrics_response() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

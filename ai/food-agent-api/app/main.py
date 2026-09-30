import logging
import time
from collections.abc import Awaitable, Callable
from contextlib import AsyncExitStack, asynccontextmanager
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

from app.api.chat import router as chat_router
from app.api.indexing import router as indexing_router
from app.core.config import get_settings
from app.core.errors import (
    FoodAgentError,
    food_agent_error_handler,
    unhandled_error_handler,
)
from app.core.logging import configure_logging
from app.core.metrics import HTTP_DURATION, HTTP_REQUESTS, metrics_response
from app.core.readiness import readiness_checks
from app.mcp.server import build_mcp_asgi_app, create_mcp_server
from app.sync.es_reconciliation import start_es_reconciliation, stop_es_reconciliation
from app.sync.es_sync_consumer import start_es_sync_consumer, stop_es_sync_consumer
from app.sync.memory_sync_consumer import (
    start_memory_sync_consumer,
    stop_memory_sync_consumer,
)

settings = get_settings()
configure_logging(settings.log_level)
logger = logging.getLogger("food_agent.api")
mcp_server = create_mcp_server(settings) if settings.mcp_enabled else None
mcp_asgi_app = (
    build_mcp_asgi_app(mcp_server, settings) if mcp_server is not None else None
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    async with AsyncExitStack() as stack:
        if mcp_server is not None:
            await stack.enter_async_context(mcp_server.session_manager.run())
        start_es_sync_consumer(settings)
        start_es_reconciliation(settings)
        start_memory_sync_consumer(settings)
        try:
            yield
        finally:
            stop_memory_sync_consumer()
            stop_es_reconciliation()
            stop_es_sync_consumer()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    lifespan=lifespan,
)
app.add_exception_handler(FoodAgentError, food_agent_error_handler)
app.add_exception_handler(Exception, unhandled_error_handler)


@app.middleware("http")
async def request_context_middleware(
    request: Request,
    call_next: Callable[[Request], Awaitable[Response]],
) -> Response:
    request_id = request.headers.get("X-Request-Id", str(uuid4()))
    request.state.request_id = request_id
    start_time = time.perf_counter()

    response = await call_next(request)

    latency_ms = (time.perf_counter() - start_time) * 1000
    response.headers["X-Request-Id"] = request_id

    route = getattr(request.scope.get("route"), "path", "unmatched")
    HTTP_REQUESTS.labels(
        method=request.method,
        route=route,
        status=str(response.status_code),
    ).inc()
    HTTP_DURATION.labels(method=request.method, route=route).observe(
        latency_ms / 1000
    )

    logger.info(
        "http_request request_id=%s method=%s path=%s status_code=%s latency_ms=%.2f",
        request_id,
        request.method,
        request.url.path,
        response.status_code,
        latency_ms,
    )
    return response


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/live")
def liveness() -> dict[str, str]:
    return {"status": "ok", "version": settings.app_version}


@app.get("/health/ready")
def readiness() -> JSONResponse:
    checks = readiness_checks(settings)
    ready = all(checks.values())
    return JSONResponse(
        status_code=200 if ready else 503,
        content={
            "status": "ready" if ready else "not_ready",
            "version": settings.app_version,
            "checks": checks,
        },
    )


@app.get("/metrics", include_in_schema=False)
def metrics() -> Response:
    return metrics_response()


app.include_router(chat_router)
app.include_router(indexing_router)
if mcp_asgi_app is not None:
    app.mount(settings.mcp_mount_path, mcp_asgi_app)

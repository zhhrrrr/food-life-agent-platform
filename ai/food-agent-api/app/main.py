import logging
import time
from collections.abc import Awaitable, Callable
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import Response

from app.api.chat import router as chat_router
from app.api.indexing import router as indexing_router
from app.core.config import get_settings
from app.core.errors import (
    FoodAgentError,
    food_agent_error_handler,
    unhandled_error_handler,
)
from app.core.logging import configure_logging
from app.sync.es_sync_consumer import start_es_sync_consumer, stop_es_sync_consumer
from app.sync.es_reconciliation import start_es_reconciliation, stop_es_reconciliation

settings = get_settings()
configure_logging(settings.log_level)
logger = logging.getLogger("food_agent.api")

app = FastAPI(title=settings.app_name, version=settings.app_version)
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


app.include_router(chat_router)
app.include_router(indexing_router)


@app.on_event("startup")
def startup() -> None:
    start_es_sync_consumer(settings)
    start_es_reconciliation(settings)


@app.on_event("shutdown")
def shutdown() -> None:
    stop_es_reconciliation()
    stop_es_sync_consumer()

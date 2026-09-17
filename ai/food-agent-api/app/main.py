import logging
import time
from collections.abc import Awaitable, Callable
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import Response

from app.api.chat import router as chat_router
from app.core.config import get_settings
from app.core.errors import (
    FoodAgentError,
    food_agent_error_handler,
    unhandled_error_handler,
)
from app.core.logging import configure_logging

settings = get_settings()
configure_logging(settings.log_level)
logger = logging.getLogger("food_agent.api")

app = FastAPI(title=settings.app_name, version=settings.app_version)
app.add_exception_handler(FoodAgentError, food_agent_error_handler) # 只要程序里抛出 FoodAgentError，就交给 food_agent_error_handler 处理。
app.add_exception_handler(Exception, unhandled_error_handler)

# 所有 HTTP 请求进入具体接口之前，先统一经过的一层处理逻辑；接口返回之后，也会再经过它
@app.middleware("http")
async def request_context_middleware(
    request: Request,
    # Callable[[Request], Awaitable[Response]]
    # call_next 是一个可调用对象，它接收一个 Request，返回一个“可被 await 的对象”，最终得到 Response。
    call_next: Callable[[Request], Awaitable[Response]],
) -> Response:
     # 请求进入路由处理前
    request_id = request.headers.get("X-Request-Id", str(uuid4()))
    request.state.request_id = request_id
    start_time = time.perf_counter()

    # 继续执行后续中间件和路由处理函数
    response = await call_next(request)

    # 路由处理完成、响应返回后
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

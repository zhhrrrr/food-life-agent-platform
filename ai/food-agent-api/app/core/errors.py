from fastapi import Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel


class ErrorResponse(BaseModel):
    code: str
    message: str
    request_id: str


class FoodAgentError(Exception):
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(message)


async def food_agent_error_handler(
    request: Request,
    exc: FoodAgentError,
) -> JSONResponse:
    request_id = getattr(request.state, "request_id", "") # getattr(object, "属性名", 默认值)
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            code=exc.code,
            message=exc.message,
            request_id=request_id,
        ).model_dump(), # model_dump() 是 Pydantic v2 里把 BaseModel 对象转换成 dict 的方法
    )


async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
    request_id = getattr(request.state, "request_id", "")
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            code="INTERNAL_ERROR",
            message="Internal server error",
            request_id=request_id,
        ).model_dump(),
    )

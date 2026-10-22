from uuid import uuid4

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field

from app.core.config import get_settings
from app.core.security import trusted_user_id
from app.trade.service import get_trade_service
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.actions import TradeActionClient, build_action_registry
from app.trade.gateway_client import TradeGatewayClient

router = APIRouter(prefix="/api/agent/trade", tags=["trade-agent"])


class TradeTurnRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message: str = Field(default="", max_length=4000)
    session_id: str = Field(default_factory=lambda: uuid4().hex, pattern=r"^[A-Za-z0-9_-]{1,64}$")
    thread_id: str | None = Field(default=None, pattern=r"^[a-f0-9]{32}$")
    request_id: str = Field(default_factory=lambda: uuid4().hex, pattern=r"^[A-Za-z0-9_-]{1,64}$")
    confirmation_id: str | None = Field(default=None, pattern=r"^[a-f0-9]{32}$")
    approved: bool | None = Field(default=None, strict=True)


def identity(request: Request) -> tuple[str, str]:
    user_id = trusted_user_id(request.headers, get_settings())
    auth = request.headers.get("authorization", "")
    if not user_id or not auth.strip():
        raise HTTPException(401, "请通过Gateway登录后访问交易工作流。")
    return user_id, auth


@router.get("/tools")
def trade_tools(request: Request):
    identity(request)
    settings = get_settings()
    catalog = []
    for registry, risk in [(build_trade_tool_registry(TradeGatewayClient(settings)), "read"),
                           (build_action_registry(TradeActionClient(settings)), "write")]:
        for schema in registry.llm_schemas():
            catalog.append({**schema, "risk": risk, "requires_confirmation": risk == "write"})
    return {"tools": catalog}


@router.post("/chat")
def trade_chat(body: TradeTurnRequest, request: Request):
    user_id, auth = identity(request)
    return get_trade_service().turn(user_id=user_id, authorization=auth, **body.model_dump())


@router.get("/threads/{thread_id}")
def trade_state(thread_id: str, request: Request):
    user_id, _ = identity(request)
    return get_trade_service().view(user_id, thread_id)


@router.post("/threads/{thread_id}/recover")
def recover_trade(thread_id: str, body: TradeTurnRequest, request: Request):
    user_id, auth = identity(request)
    if body.message or body.approved is not None or body.confirmation_id is not None or (body.thread_id and body.thread_id != thread_id):
        raise HTTPException(422, "恢复请求只接受原会话和request_id。")
    return get_trade_service().turn(user_id=user_id, authorization=auth, session_id=body.session_id,
                                    thread_id=thread_id, request_id=body.request_id, recover=True)

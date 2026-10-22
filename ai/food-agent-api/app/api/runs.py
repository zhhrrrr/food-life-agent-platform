"""Long-run API: accepted jobs survive SSE disconnects; events can be replayed."""
import asyncio
import json
from uuid import uuid4

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field

from app.api.chat import ChatRequest, ChatResponse, session_memory
from app.api.trade import identity
from app.orchestration.service import get_orchestration_service

router = APIRouter(prefix="/api/agent/runs", tags=["agent-runs"])


class ResumeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    session_id: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    request_id: str = Field(default_factory=lambda: uuid4().hex, pattern=r"^[A-Za-z0-9_-]{1,64}$")
    message: str = Field(default="", max_length=4000)
    task_id: str | None = Field(default=None, pattern=r"^[a-z][a-z0-9_]{0,63}$")
    confirmation_id: str | None = Field(default=None, pattern=r"^[a-f0-9]{32}$")
    approved: bool | None = Field(default=None, strict=True)
    recover: bool = False


@router.post("", status_code=202)
def start_run(body: ChatRequest, request: Request):
    user_id, auth = identity(request)
    if body.trade_thread_id:
        raise HTTPException(422, "旧交易线程请使用交易恢复接口。")
    session_id = body.session_id or uuid4().hex
    context, _ = session_memory.load(session_id, user_id=user_id, authorization=auth)

    return get_orchestration_service().submit(user_id=user_id, authorization=auth,
        session_id=session_id, request_id=body.request_id or uuid4().hex,
        message=body.message, session_history=context.history(session_memory.settings.session_memory_max_turns),
        run_id=body.run_id, task_id=body.task_id, approved=body.approved, confirmation_id=body.confirmation_id,
        )


@router.get("/{run_id}", response_model=ChatResponse)
def get_run(run_id: str, request: Request):
    user_id, _ = identity(request)
    return get_orchestration_service().view(user_id, run_id)


@router.post("/{run_id}/resume", status_code=202)
def resume_run(run_id: str, body: ResumeRequest, request: Request):
    user_id, auth = identity(request)
    return get_orchestration_service().submit(user_id=user_id, authorization=auth, run_id=run_id,
        **body.model_dump())


@router.post("/{run_id}/pause", response_model=ChatResponse)
def pause_run(run_id: str, request: Request):
    user_id, _ = identity(request)
    return get_orchestration_service().pause(user_id, run_id)


@router.get("/{run_id}/events")
async def stream_events(run_id: str, request: Request, after: int = Query(default=0, ge=0), request_id: str | None = None):
    user_id, _ = identity(request)
    service = get_orchestration_service()
    service.view(user_id, run_id)  # authorize before starting streaming headers
    try:
        cursor = max(after, int(request.headers.get("last-event-id", "0")))
    except ValueError:
        raise HTTPException(422, "事件游标格式无效。")

    async def events():
        nonlocal cursor
        ticks = 0
        while not await request.is_disconnected():
            for item in service.events(user_id, run_id, cursor):
                cursor = item["id"]
                if request_id and item["data"].get("request_id") != request_id:
                    continue
                yield f"id: {cursor}\nevent: {item['event']}\ndata: {json.dumps(item['data'], ensure_ascii=False)}\n\n"
                if item["event"] in {"turn_finished", "error"}:
                    return
            ticks += 1
            if ticks % 50 == 0:
                yield ": heartbeat\n\n"
            await asyncio.sleep(0.2)
    return StreamingResponse(events(), media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

import logging
import time
from uuid import uuid4

from fastapi import APIRouter, Request
from pydantic import BaseModel, ConfigDict, Field

from app.core.errors import FoodAgentError
from app.core.metrics import CHAT_REQUESTS
from app.core.security import trusted_user_id
from app.graph.food_graph import run_food_agent
from app.memory.long_term_memory import LongTermMemoryService
from app.memory.session_memory import SessionMemoryService
from app.search.schemas import Reference

logger = logging.getLogger("food_agent.api.chat")
router = APIRouter(prefix="/api/agent", tags=["agent"])
session_memory = SessionMemoryService()
long_term_memory = LongTermMemoryService()


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    message: str = Field(..., min_length=1, max_length=4000)
    session_id: str | None = Field(
        default=None,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
    )


class ChatResponse(BaseModel):
    session_id: str
    run_id: str
    plan_revision: int
    workflow: str
    execution_status: str
    intent: str
    agent: str
    agents: list[str] = Field(default_factory=list)
    response: str
    router_source: str
    router_confidence: float
    rag_mode: str
    references: list[Reference] = Field(default_factory=list)
    task_statuses: list[dict[str, object]] = Field(default_factory=list)
    memory_status: str


@router.post("/chat", response_model=ChatResponse)
def chat(chat_request: ChatRequest, request: Request) -> ChatResponse:
    request_id = getattr(request.state, "request_id", "")
    session_id = chat_request.session_id or uuid4().hex
    authorization = request.headers.get("authorization", "")
    user_id = trusted_user_id(request.headers, session_memory.settings)
    start_time = time.perf_counter()

    try:
        context, load_status = session_memory.load(
            session_id,
            user_id=user_id,
            authorization=authorization,
        )
        result = run_food_agent(
            chat_request.message,
            authorization=authorization,
            session_history=context.history(session_memory.settings.session_memory_max_turns),
            session_id=session_id,
            user_id=user_id,
        )
        write_status = session_memory.append(
            session_id,
            user_id=user_id,
            authorization=authorization,
            user=chat_request.message,
            assistant=result["response"],
            intent=result["intent"],
            run_id=result["run_id"],
        )
        memory_status = f"{load_status}->{write_status}"
        try:
            long_term_memory.remember_explicit(
                chat_request.message,
                user_id,
                authorization,
                result["run_id"],
            )
        except Exception as exc:  # noqa: BLE001 - long-term memory is non-blocking
            logger.warning("long_term_memory_write_failed session_id=%s error=%s", session_id, type(exc).__name__)
    except Exception as exc:
        logger.exception(
            "agent_chat_failed request_id=%s session_id=%s",
            request_id,
            session_id,
        )
        raise FoodAgentError(
            code="AGENT_CHAT_FAILED",
            message="Agent chat failed",
        ) from exc

    latency_ms = (time.perf_counter() - start_time) * 1000
    logger.info(
        "agent_chat_completed request_id=%s session_id=%s intent=%s "
        "agent=%s workflow=%s execution_status=%s router_source=%s "
        "rag_mode=%s latency_ms=%.2f",
        request_id,
        session_id,
        result["intent"],
        result["agent"],
        result["workflow"],
        result["execution_status"],
        result["router_source"],
        result.get("rag_mode", ""),
        latency_ms,
    )
    CHAT_REQUESTS.labels(
        intent=result["intent"],
        execution_status=result["execution_status"],
    ).inc()

    return ChatResponse(
        session_id=session_id,
        run_id=result["run_id"],
        plan_revision=result["plan_revision"],
        workflow=result["workflow"],
        execution_status=result["execution_status"],
        intent=result["intent"],
        agent=result["agent"],
        agents=result.get("agents", []),
        response=result["response"],
        router_source=result["router_source"],
        router_confidence=result["router_confidence"],
        rag_mode=result.get("rag_mode", ""),
        references=result.get("references", []),
        task_statuses=[
            {
                "task_id": item.get("task_id"),
                "agent": item.get("agent"),
                "status": item.get("status"),
                "attempts": item.get("attempts", 0),
                "error_code": "TASK_FAILED" if item.get("error") else "",
            }
            for item in result.get("task_results", [])
        ],
        memory_status=memory_status,
    )

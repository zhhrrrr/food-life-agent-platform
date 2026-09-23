import logging
import time

from fastapi import APIRouter, Request
from pydantic import BaseModel, Field

from app.core.errors import FoodAgentError
from app.graph.food_graph import run_food_agent
from app.search.schemas import Reference

logger = logging.getLogger("food_agent.api.chat")
router = APIRouter(prefix="/api/agent", tags=["agent"])


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1)


class ChatResponse(BaseModel):
    intent: str
    agent: str
    response: str
    references: list[Reference] = Field(default_factory=list)


@router.post("/chat", response_model=ChatResponse)
def chat(chat_request: ChatRequest, request: Request) -> ChatResponse:
    request_id = getattr(request.state, "request_id", "")
    start_time = time.perf_counter()

    try:
        result = run_food_agent(
            chat_request.message,
            authorization=request.headers.get("authorization", ""),
        )
    except Exception as exc:
        logger.exception(
            "agent_chat_failed request_id=%s message=%s",
            request_id,
            chat_request.message,
        )
        raise FoodAgentError(
            code="AGENT_CHAT_FAILED",
            message="Agent chat failed",
        ) from exc

    latency_ms = (time.perf_counter() - start_time) * 1000
    logger.info(
        "agent_chat_completed request_id=%s intent=%s agent=%s latency_ms=%.2f message=%s",
        request_id,
        result["intent"],
        result["agent"],
        latency_ms,
        chat_request.message,
    )

    return ChatResponse(
        intent=result["intent"],
        agent=result["agent"],
        response=result["response"],
        references=result.get("references", []),
    )

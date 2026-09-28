import logging
import time
from uuid import uuid4

from fastapi import APIRouter, Request
from pydantic import BaseModel, Field

from app.core.errors import FoodAgentError
from app.graph.food_graph import run_food_agent
from app.memory.conversation_memory import RedisConversationMemory
from app.search.schemas import Reference

logger = logging.getLogger("food_agent.api.chat")
router = APIRouter(prefix="/api/agent", tags=["agent"])
conversation_memory = RedisConversationMemory()


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=4000)
    conversation_id: str | None = Field(
        default=None,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
    )


class ChatResponse(BaseModel):
    conversation_id: str
    intent: str
    agent: str
    response: str
    router_source: str
    router_confidence: float
    rag_mode: str
    references: list[Reference] = Field(default_factory=list)


@router.post("/chat", response_model=ChatResponse)
def chat(chat_request: ChatRequest, request: Request) -> ChatResponse:
    request_id = getattr(request.state, "request_id", "")
    conversation_id = chat_request.conversation_id or uuid4().hex
    start_time = time.perf_counter()

    try:
        history = conversation_memory.load(conversation_id)
        result = run_food_agent(
            chat_request.message,
            authorization=request.headers.get("authorization", ""),
            conversation_history=history,
        )
        conversation_memory.append(
            conversation_id,
            user=chat_request.message,
            assistant=result["response"],
            intent=result["intent"],
        )
    except Exception as exc:
        logger.exception(
            "agent_chat_failed request_id=%s conversation_id=%s",
            request_id,
            conversation_id,
        )
        raise FoodAgentError(
            code="AGENT_CHAT_FAILED",
            message="Agent chat failed",
        ) from exc

    latency_ms = (time.perf_counter() - start_time) * 1000
    logger.info(
        "agent_chat_completed request_id=%s conversation_id=%s intent=%s "
        "agent=%s router_source=%s rag_mode=%s latency_ms=%.2f",
        request_id,
        conversation_id,
        result["intent"],
        result["agent"],
        result["router_source"],
        result.get("rag_mode", ""),
        latency_ms,
    )

    return ChatResponse(
        conversation_id=conversation_id,
        intent=result["intent"],
        agent=result["agent"],
        response=result["response"],
        router_source=result["router_source"],
        router_confidence=result["router_confidence"],
        rag_mode=result.get("rag_mode", ""),
        references=result.get("references", []),
    )

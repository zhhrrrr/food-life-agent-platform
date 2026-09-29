import logging
import math
from threading import Lock
from time import monotonic
from typing import Any

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.graph.route_examples import ROUTE_EXAMPLES, RouteExample, SemanticIntent
from app.llm.embedding_model import build_embedding_model
from app.memory.conversation_memory import (
    ConversationTurn,
    should_use_conversation_history,
)

logger = logging.getLogger("food_agent.graph.semantic_router")


class SemanticRouteMatch(BaseModel):
    intent: SemanticIntent
    score: float = Field(ge=0.0, le=1.0)
    margin: float = Field(ge=0.0, le=1.0)
    matched_example: str
    accepted: bool


class SemanticRouter:
    def __init__(
        self,
        settings: Settings | None = None,
        embedding_model: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.embedding_model = embedding_model
        self._route_vectors: list[list[float]] | None = None
        self._cache_lock = Lock()
        self._retry_after = 0.0

    def match(
        self,
        message: str,
        history: list[ConversationTurn] | None = None,
    ) -> SemanticRouteMatch | None:
        if not self.settings.semantic_router_enabled:
            return None
        if monotonic() < self._retry_after:
            return None

        try:
            model = self.embedding_model or build_embedding_model(self.settings)
            route_vectors = self._load_route_vectors(model)
            query_vector = _validate_vector(
                model.embed_query(build_semantic_query(message, history or []))
            )
            scored = [
                (example, cosine_similarity(query_vector, vector))
                for example, vector in zip(ROUTE_EXAMPLES, route_vectors, strict=True)
            ]
            self._retry_after = 0.0
        except Exception as exc:  # noqa: BLE001 - external embedding boundary
            self._retry_after = (
                monotonic() + self.settings.semantic_router_failure_cooldown_seconds
            )
            logger.warning(
                "semantic_router_embedding_failed error=%s", type(exc).__name__
            )
            return None

        return score_semantic_route(
            scored,
            high_threshold=self.settings.semantic_router_high_threshold,
            min_margin=self.settings.semantic_router_min_margin,
        )

    def _load_route_vectors(self, model: Any) -> list[list[float]]:
        if self._route_vectors is not None:
            return self._route_vectors
        with self._cache_lock:
            if self._route_vectors is None:
                # langchain自带
                vectors = model.embed_documents(
                    [example.text for example in ROUTE_EXAMPLES]
                )
                if len(vectors) != len(ROUTE_EXAMPLES):
                    raise ValueError(
                        "embedding response count does not match route examples"
                    )
                self._route_vectors = [_validate_vector(vector) for vector in vectors]
        return self._route_vectors


# 最后两轮对话
def build_semantic_query(message: str, history: list[ConversationTurn]) -> str:
    recent_history = history[-2:]
    if not recent_history or not should_use_conversation_history(message):
        return message
    previous = "\n".join(
        f"上一轮用户请求（{turn.intent or 'unknown'}）：{turn.user}"
        for turn in recent_history
    )
    return f"{previous}\n当前用户请求：{message}"


def _validate_vector(vector: Any) -> list[float]:
    if not isinstance(vector, (list, tuple)) or not vector:
        raise ValueError("embedding vector is empty")
    values = [float(value) for value in vector]
    if not all(math.isfinite(value) for value in values):
        raise ValueError("embedding vector contains a non-finite value")
    if math.sqrt(sum(value * value for value in values)) == 0:
        raise ValueError("embedding vector has zero norm")
    return values


def score_semantic_route(
    scored_examples: list[tuple[RouteExample, float]],
    *,
    high_threshold: float,
    min_margin: float,
) -> SemanticRouteMatch:
    intent_scores: dict[SemanticIntent, tuple[float, str]] = {}
    for example, score in scored_examples:
        current = intent_scores.get(example.intent)
        if current is None or score > current[0]:
            intent_scores[example.intent] = (score, example.text)

    if set(intent_scores) != {"recommend", "trade", "support"}:
        raise ValueError("scored semantic route examples must cover all intents")

    ranked = sorted(
        (
            (intent, score, example)
            for intent, (score, example) in intent_scores.items()
        ),
        key=lambda item: item[1],
        reverse=True,
    )
    best_intent, best_score, best_example = ranked[0]
    normalized_score = _unit_interval(best_score)
    margin = _unit_interval(best_score - ranked[1][1])
    accepted = normalized_score >= _unit_interval(
        high_threshold
    ) and margin >= _unit_interval(min_margin)
    return SemanticRouteMatch(
        intent=best_intent,
        score=normalized_score,
        margin=margin,
        matched_example=best_example,
        accepted=accepted,
    )


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if len(left) != len(right):
        raise ValueError("embedding vector dimensions do not match")
    dot_product = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    return dot_product / (left_norm * right_norm)


def _unit_interval(value: float) -> float:
    return max(0.0, min(1.0, value))

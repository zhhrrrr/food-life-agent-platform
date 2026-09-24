import logging
import math
from threading import Lock
from time import monotonic
from typing import Any, Literal

from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.embedding_model import build_embedding_model
from app.memory.conversation_memory import ConversationTurn, should_use_conversation_history

logger = logging.getLogger("food_agent.graph.semantic_router")

SemanticIntent = Literal["recommend", "trade", "support"]

ROUTE_EXAMPLES: tuple[tuple[SemanticIntent, str], ...] = (
    ("recommend", "推荐一家适合朋友聚餐的川菜馆"),
    ("recommend", "预算两百元有什么双人套餐推荐"),
    ("recommend", "比较这两家餐厅哪家口碑更好"),
    ("recommend", "附近有什么好吃的火锅店"),
    ("recommend", "想找适合约会而且环境安静的餐厅"),
    ("trade", "查询订单详情和当前进度"),
    ("trade", "这个套餐现在多少钱还有多少库存"),
    ("trade", "查询我可以使用的优惠券"),
    ("trade", "查看这个套餐当前可参加的拼团"),
    ("trade", "预览套餐订单和折扣前金额"),
    ("trade", "取消订单或者申请退款"),
    ("support", "退款规则和售后流程是什么"),
    ("support", "拼团失败以后钱怎么退"),
    ("support", "支付失败的处理规则是什么"),
    ("support", "订单状态中的待使用是什么意思"),
    ("support", "平台优惠券的使用规则是什么"),
    ("support", "常见问题和平台帮助"),
)


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
            query_vector = _validate_vector(model.embed_query(_contextual_query(message, history or [])))
            scored = [
                (intent, example, _cosine_similarity(query_vector, vector))
                for (intent, example), vector in zip(ROUTE_EXAMPLES, route_vectors, strict=True)
            ]
            self._retry_after = 0.0
        except Exception as exc:
            self._retry_after = (
                monotonic() + self.settings.semantic_router_failure_cooldown_seconds
            )
            logger.warning("semantic_router_embedding_failed error=%s", type(exc).__name__)
            return None

        intent_scores: dict[SemanticIntent, tuple[float, str]] = {}
        for intent, example, score in scored:
            current = intent_scores.get(intent)
            if current is None or score > current[0]:
                intent_scores[intent] = (score, example)

        ranked = sorted(
            ((intent, score, example) for intent, (score, example) in intent_scores.items()),
            key=lambda item: item[1],
            reverse=True,
        )
        best_intent, best_score, best_example = ranked[0]
        second_score = ranked[1][1] if len(ranked) > 1 else 0.0
        normalized_score = _unit_interval(best_score)
        margin = _unit_interval(best_score - second_score)
        accepted = (
            normalized_score >= _unit_interval(self.settings.semantic_router_high_threshold)
            and margin >= _unit_interval(self.settings.semantic_router_min_margin)
        )
        return SemanticRouteMatch(
            intent=best_intent,
            score=normalized_score,
            margin=margin,
            matched_example=best_example,
            accepted=accepted,
        )

    def _load_route_vectors(self, model: Any) -> list[list[float]]:
        if self._route_vectors is not None:
            return self._route_vectors
        with self._cache_lock:
            if self._route_vectors is None:
                vectors = model.embed_documents([example for _, example in ROUTE_EXAMPLES])
                if len(vectors) != len(ROUTE_EXAMPLES):
                    raise ValueError("embedding response count does not match route examples")
                self._route_vectors = [_validate_vector(vector) for vector in vectors]
        return self._route_vectors


def _contextual_query(message: str, history: list[ConversationTurn]) -> str:
    recent_history = history[-2:]
    if not recent_history or not should_use_conversation_history(message):
        return message
    previous = "\n".join(
        f"Previous user request ({turn.intent or 'unknown'}): {turn.user}"
        for turn in recent_history
    )
    return f"{previous}\nCurrent user request: {message}"


def _validate_vector(vector: Any) -> list[float]:
    if not isinstance(vector, (list, tuple)) or not vector:
        raise ValueError("embedding vector is empty")
    values = [float(value) for value in vector]
    if not all(math.isfinite(value) for value in values):
        raise ValueError("embedding vector contains a non-finite value")
    if math.sqrt(sum(value * value for value in values)) == 0:
        raise ValueError("embedding vector has zero norm")
    return values


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    if len(left) != len(right):
        raise ValueError("embedding vector dimensions do not match")
    dot_product = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    return dot_product / (left_norm * right_norm)


def _unit_interval(value: float) -> float:
    return max(0.0, min(1.0, value))

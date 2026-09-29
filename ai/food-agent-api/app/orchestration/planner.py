from __future__ import annotations

import json
import logging
import re
from functools import lru_cache
from uuid import uuid4

from app.core.config import Settings, get_settings
from app.graph.semantic_router import SemanticRouter
from app.llm.structured_output import invoke_structured_output
from app.memory.conversation_memory import (
    ConversationTurn,
    should_use_conversation_history,
)
from app.orchestration.models import (
    AgentName,
    AgentTask,
    ExecutionPlan,
    PlanDraft,
    TaskResult,
)
from app.orchestration.registry import DEFAULT_CAPABILITY
from app.orchestration.validator import (
    PlanValidationError,
    validate_plan,
    validate_replan,
)
from app.trade.policy import detect_trade_mutation

logger = logging.getLogger("food_agent.orchestration.planner")

RECOMMEND_MARKERS = (
    "推荐",
    "找一家",
    "吃什么",
    "哪家",
    "餐厅",
    "餐馆",
    "商家",
    "套餐",
    "菜系",
    "火锅",
    "烧烤",
    "川菜",
    "约会",
    "聚餐",
    "评价",
    "对比",
    "比较",
    "预算",
    "好吃",
    "restaurant",
    "recommend",
)
SUPPORT_MARKERS = (
    "规则",
    "售后",
    "FAQ",
    "faq",
    "是什么意思",
    "为什么不能",
    "退款条件",
    "支付规则",
    "拼团规则",
)
TRADE_MARKERS = (
    "订单",
    "库存",
    "实时价格",
    "当前价格",
    "优惠券",
    "拼团",
    "订单号",
    "packageid",
    "orderid",
    "teamid",
)
GENERAL_FAST_MARKERS = (
    "你好",
    "您好",
    "谢谢",
    "感谢",
    "你是谁",
    "能做什么",
    "有什么功能",
    "支持什么",
    "hello",
    "hi",
)
ORDER_FACT_PATTERN = re.compile(
    r"(?:订单(?:号|ID|id|编号)?|order(?:\s*id|\s*no)?)\s*[:：]?\s*\d{1,19}",
    re.IGNORECASE,
)
ORDER_FACT_MARKERS = ("我的订单", "当前订单", "订单状态", "订单进度", "订单详情")


class DynamicPlanner:
    def __init__(
        self,
        settings: Settings | None = None,
        *,
        chat_model: object | None = None,
        semantic_router: SemanticRouter | None = None,
    ) -> None:
        uses_default_settings = settings is None
        self.settings = settings or get_settings()
        self.planner_settings = self.settings.model_copy(
            update={
                "llm_timeout_seconds": self.settings.orchestration_planner_timeout_seconds
            }
        )
        self.chat_model = chat_model
        self.semantic_router = semantic_router or (
            _default_semantic_router()
            if uses_default_settings
            else SemanticRouter(settings=self.settings)
        )

    def plan(
        self,
        message: str,
        history: list[ConversationTurn] | None = None,
    ) -> ExecutionPlan:
        conversation_history = history or []
        fallback = _rules_plan(message, conversation_history)
        signals = detect_domain_agents(message)

        if not signals and any(
            marker.casefold() in message.casefold() for marker in GENERAL_FAST_MARKERS
        ):
            return self._validate_or_general(fallback, message)

        if len(signals) == 1:
            semantic_plan = self._semantic_plan(
                message,
                signals[0],
                conversation_history,
            )
            if semantic_plan is not None:
                return semantic_plan

        if not self.settings.llm_router_enabled:
            return self._validate_or_general(fallback, message)

        try:
            draft = invoke_structured_output(
                settings=self.planner_settings,
                schema=PlanDraft,
                messages=_planner_messages(message, conversation_history),
                chat_model=self.chat_model,
            )
            candidate = _materialize_draft(
                draft,
                goal=message,
                run_id=uuid4().hex,
                revision=1,
                source="llm",
            )
            return self._validate_or_general(candidate, message)
        except Exception as exc:  # noqa: BLE001 - planner boundary must degrade safely
            logger.warning(
                "dynamic_plan_fallback error=%s detail=%s",
                type(exc).__name__,
                exc,
            )
            return self._validate_or_general(fallback, message)

    def replan(
        self,
        plan: ExecutionPlan,
        results: list[TaskResult],
    ) -> ExecutionPlan | None:
        if not self.settings.llm_router_enabled:
            return None
        try:
            draft = invoke_structured_output(
                settings=self.planner_settings,
                schema=PlanDraft,
                messages=_replan_messages(plan, results),
                chat_model=self.chat_model,
            )
            revised = _materialize_draft(
                draft,
                goal=plan.goal,
                run_id=plan.run_id,
                revision=plan.revision + 1,
                source="replan",
            )
            validate_replan(plan, revised)
            return validate_plan(
                revised,
                max_tasks=self.settings.orchestration_max_tasks,
                max_depth=self.settings.orchestration_max_depth,
            )
        except Exception as exc:  # noqa: BLE001 - replanning is optional recovery
            logger.warning(
                "dynamic_replan_skipped error=%s detail=%s",
                type(exc).__name__,
                exc,
            )
            return None

    def _validate_or_general(
        self,
        plan: ExecutionPlan,
        message: str,
    ) -> ExecutionPlan:
        try:
            return validate_plan(
                plan,
                max_tasks=self.settings.orchestration_max_tasks,
                max_depth=self.settings.orchestration_max_depth,
            )
        except (PlanValidationError, ValueError) as exc:
            logger.warning("invalid_dynamic_plan reason=%s", exc)
            return validate_plan(_single_plan(message, "general", 0.4, str(exc), "rules"))

    def _semantic_plan(
        self,
        message: str,
        expected_agent: AgentName,
        history: list[ConversationTurn],
    ) -> ExecutionPlan | None:
        match = self.semantic_router.match(message, history)
        if match is None or not match.accepted or match.intent != expected_agent:
            return None
        return validate_plan(
            _single_plan(
                message,
                expected_agent,
                confidence=match.score,
                reason=f"semantic match: {match.matched_example}",
                source="semantic",
            ),
            max_tasks=self.settings.orchestration_max_tasks,
            max_depth=self.settings.orchestration_max_depth,
        )


def detect_domain_agents(message: str) -> list[AgentName]:
    agents: list[AgentName] = []
    if any(marker.casefold() in message.casefold() for marker in RECOMMEND_MARKERS):
        agents.append("recommend")
    if any(marker.casefold() in message.casefold() for marker in SUPPORT_MARKERS):
        agents.append("support")
    if any(marker.casefold() in message.casefold() for marker in TRADE_MARKERS):
        agents.append("trade")
    if detect_trade_mutation(message) and "trade" not in agents:
        agents.append("trade")
    return agents


@lru_cache(maxsize=1)
def _default_semantic_router() -> SemanticRouter:
    return SemanticRouter(settings=get_settings())


def _rules_plan(
    message: str,
    history: list[ConversationTurn],
) -> ExecutionPlan:
    agents = detect_domain_agents(message)
    if not agents and history and should_use_conversation_history(message):
        previous = history[-1].intent
        if previous in {"recommend", "support", "trade"}:
            agents = [previous]  # type: ignore[list-item]
    if not agents:
        return _single_plan(
            message,
            "general",
            0.75,
            "no supported business domain was detected",
            "rules",
        )
    if len(agents) == 1:
        return _single_plan(
            message,
            agents[0],
            0.65,
            f"rule matched {agents[0]} domain",
            "rules",
        )

    tasks: list[AgentTask] = []
    if "recommend" in agents:
        tasks.append(
            AgentTask(
                id="recommend_candidates",
                agent="recommend",
                capability="recommend.retrieve",
                objective=message,
            )
        )
    if "trade" in agents:
        dependencies = ["recommend_candidates"] if "recommend" in agents else []
        tasks.append(
            AgentTask(
                id="trade_facts",
                agent="trade",
                capability="trade.query",
                objective=message,
                depends_on=dependencies,
            )
        )
    if "support" in agents:
        needs_order_facts = _needs_order_facts(message)
        dependencies = (
            ["trade_facts"] if "trade" in agents and needs_order_facts else []
        )
        tasks.append(
            AgentTask(
                id="support_evidence",
                agent="support",
                capability="support.explain" if needs_order_facts else "support.retrieve",
                objective=message,
                depends_on=dependencies,
            )
        )
    return ExecutionPlan(
        run_id=uuid4().hex,
        goal=message,
        tasks=tasks,
        confidence=0.7,
        reason="rule planner detected a cross-domain request",
        source="rules",
    )


def _single_plan(
    message: str,
    agent: AgentName,
    confidence: float,
    reason: str,
    source: str,
) -> ExecutionPlan:
    return ExecutionPlan(
        run_id=uuid4().hex,
        goal=message,
        tasks=[
            AgentTask(
                id=f"{agent}_task",
                agent=agent,
                capability=DEFAULT_CAPABILITY[agent],
                objective=message,
            )
        ],
        confidence=confidence,
        reason=reason,
        source=source,  # type: ignore[arg-type]
    )


def _materialize_draft(
    draft: PlanDraft,
    *,
    goal: str,
    run_id: str,
    revision: int,
    source: str,
) -> ExecutionPlan:
    normalized_ids: dict[str, str] = {}
    used_ids: set[str] = set()
    for index, task in enumerate(draft.tasks, start=1):
        candidate = _normalize_task_id(task.id, task.agent, index)
        suffix = 2
        unique_id = candidate
        while unique_id in used_ids:
            unique_id = f"{candidate}_{suffix}"
            suffix += 1
        normalized_ids[task.id] = unique_id
        used_ids.add(unique_id)

    tasks = [
        AgentTask(
            id=normalized_ids[task.id],
            agent=task.agent,
            capability=_normalize_capability(task.agent, task.capability),
            objective=task.objective.strip() or goal,
            depends_on=[normalized_ids.get(item, item) for item in task.depends_on],
            required=task.required if isinstance(task.required, bool) else True,
        )
        for task in draft.tasks
    ]
    if not _needs_order_facts(goal):
        trade_task_ids = {task.id for task in tasks if task.agent == "trade"}
        tasks = [
            task.model_copy(
                update={
                    "capability": (
                        "support.retrieve"
                        if task.agent == "support"
                        else task.capability
                    ),
                    "depends_on": [
                        dependency
                        for dependency in task.depends_on
                        if dependency not in trade_task_ids
                    ],
                }
            )
            for task in tasks
        ]
    return ExecutionPlan(
        run_id=run_id,
        revision=revision,
        goal=goal,
        tasks=tasks,
        confidence=draft.confidence,
        reason=draft.reason,
        source=source,  # type: ignore[arg-type]
    )


def _normalize_task_id(raw_id: str, agent: AgentName, index: int) -> str:
    cleaned = "".join(
        character if character.isascii() and character.isalnum() else "_"
        for character in raw_id.casefold()
    ).strip("_")
    if not cleaned or not cleaned[0].isalpha():
        cleaned = f"{agent}_{cleaned or index}"
    return cleaned[:64]


def _normalize_capability(agent: AgentName, capability: str) -> str:
    normalized = capability.strip().casefold()
    aliases = {
        ("recommend", "retrieve"): "recommend.retrieve",
        ("recommend", "search"): "recommend.retrieve",
        ("recommend", "rerank"): "recommend.rerank",
        ("support", "retrieve"): "support.retrieve",
        ("support", "search"): "support.retrieve",
        ("support", "explain"): "support.explain",
        ("trade", "query"): "trade.query",
        ("general", "respond"): "general.respond",
        ("general", "chat"): "general.respond",
    }
    return aliases.get((agent, normalized), normalized)


def _needs_order_facts(message: str) -> bool:
    return bool(ORDER_FACT_PATTERN.search(message)) or any(
        marker in message for marker in ORDER_FACT_MARKERS
    )


def _planner_messages(
    message: str,
    history: list[ConversationTurn],
) -> list[tuple[str, str]]:
    system = (
        "Create a bounded execution DAG for a food-life multi-agent system. "
        "Allowed agent/capability pairs are recommend/recommend.retrieve or recommend.rerank, "
        "support/support.retrieve or support.explain, trade/trade.query, and "
        "general/general.respond. Use general alone for casual, unclear, or out-of-domain input. "
        "Use dependencies when a later task needs IDs or facts from an earlier task. "
        "A recommendation that asks for current price, stock, or coupons must retrieve candidates "
        "before trade.query. A concrete order requiring policy explanation should query trade before "
        "support.explain. Every task must contain id, agent, capability, objective, depends_on, and "
        "required. The top-level object must also contain confidence and reason. Task IDs must start "
        "with a lowercase letter. Create at most 6 tasks and never create write operations."
    )
    messages: list[tuple[str, str]] = [("system", system)]
    for turn in history[-4:]:
        messages.append(("human", turn.user[:500]))
        if turn.assistant:
            messages.append(("assistant", turn.assistant[:500]))
    messages.append(("human", message))
    return messages


def _replan_messages(
    plan: ExecutionPlan,
    results: list[TaskResult],
) -> list[tuple[str, str]]:
    return [
        (
            "system",
            (
                "Repair the failed food-life execution DAG. Keep successful task IDs and their "
                "original definitions unchanged, remove or replace failed branches, use only "
                "registered read-only capabilities, and return a complete PlanDraft with no "
                "dependency cycles."
            ),
        ),
        (
            "human",
            json.dumps(
                {
                    "plan": plan.model_dump(),
                    "results": [result.model_dump() for result in results],
                },
                ensure_ascii=False,
                default=str,
            )[:12000],
        ),
    ]

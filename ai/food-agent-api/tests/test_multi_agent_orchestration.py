from __future__ import annotations

from time import sleep

from app.core.config import Settings
from langgraph.checkpoint.memory import InMemorySaver

from app.graph.food_graph import build_food_graph, execution_view
from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.planner import DynamicPlanner
from app.orchestration.registry import CapabilitySpec
from app.orchestration.validator import PlanValidationError, validate_plan


def _plan(*tasks: AgentTask, revision: int = 1) -> ExecutionPlan:
    return ExecutionPlan(
        run_id="run-12345678",
        revision=revision,
        goal="test dynamic DAG",
        tasks=list(tasks),
        confidence=0.9,
        reason="test",
        source="rules" if revision == 1 else "replan",
    )


def _execute_read_plan(plan, registry, *, max_replans=0, replanner=None):
    """Exercise the same scheduler as HTTP runs, with in-memory checkpoints."""
    class FixedPlanner:
        def plan(self, message, history=None):
            return plan

        def replan(self, current, results):
            return replanner(current, results) if replanner else None

    graph = build_food_graph(
        Settings(long_term_memory_enabled=False, orchestration_max_replans=max_replans),
        InMemorySaver(), planner=FixedPlanner(), registry=registry, adapter=None,
    )
    state = graph.invoke(
        {"message": plan.goal, "run_id": plan.run_id, "user_id": "1", "session_id": "test"},
        {"configurable": {"thread_id": plan.run_id}},
        context={"authorization": "", "lease_check": lambda: None,
                 "paused": lambda: False, "emit": lambda kind, payload: None},
    )
    return execution_view(state)


def test_rules_planner_builds_recommend_then_trade_dag() -> None:
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))

    plan = planner.plan("推荐一个双人套餐，再查询实时库存")

    assert [task.agent for task in plan.tasks] == ["recommend", "trade"]
    assert plan.tasks[1].depends_on == ["recommend_candidates"]
    assert plan.source == "rules"


def test_rules_planner_builds_trade_then_support_dag() -> None:
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))

    plan = planner.plan("订单123为什么不能退款，退款规则是什么")

    assert [task.agent for task in plan.tasks] == ["support", "trade"] or [
        task.agent for task in plan.tasks
    ] == ["trade", "support"]
    task_by_agent = {task.agent: task for task in plan.tasks}
    assert task_by_agent["support"].depends_on == ["trade_facts"]


def test_rules_planner_does_not_query_trade_for_generic_status_meaning() -> None:
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))

    plan = planner.plan("待使用订单状态是什么意思")

    assert [task.agent for task in plan.tasks] == ["support"]


def test_rules_planner_routes_current_order_query_to_trade() -> None:
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))

    plan = planner.plan("查询我的订单进度")

    assert [task.agent for task in plan.tasks] == ["trade"]


def test_llm_planner_normalizes_draft_before_strict_validation() -> None:
    class StructuredModel:
        def invoke(self, messages: list[tuple[str, str]]) -> dict[str, object]:
            assert messages[-1][0] == "human"
            return {
                "confidence": 0.91,
                "reason": "recommendation requires real-time verification",
                "tasks": [
                    {
                        "id": "1",
                        "agent": "recommend",
                        "capability": "retrieve",
                        "dependencies": [],
                        "required": ["budget"],
                    },
                    {
                        "id": "2",
                        "agent": "trade",
                        "capability": "query",
                        "dependencies": ["1"],
                        "required": ["package_id"],
                    },
                ],
            }

    class ChatModel:
        def with_structured_output(
            self, schema: object, **kwargs: object
        ) -> StructuredModel:
            return StructuredModel()

    planner = DynamicPlanner(
        settings=Settings(llm_router_enabled=True),
        chat_model=ChatModel(),
    )

    plan = planner.plan("推荐一个双人套餐，再查询实时库存")

    assert plan.source == "llm"
    assert [task.id for task in plan.tasks] == ["recommend_1", "trade_2"]
    assert [task.capability for task in plan.tasks] == [
        "recommend.run",
        "trade.query",
    ]
    assert plan.tasks[1].depends_on == ["recommend_1"]
    assert all(task.required is True for task in plan.tasks)


def test_unrelated_request_routes_to_general_agent() -> None:
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))

    plan = planner.plan("你好，你能做什么")

    assert len(plan.tasks) == 1
    assert plan.tasks[0].agent == "general"
    assert plan.tasks[0].capability == "general.respond"


def test_plan_validator_rejects_cycles_and_cross_agent_capabilities() -> None:
    cyclic = _plan(
        AgentTask(
            id="first",
            agent="recommend",
            capability="recommend.run",
            objective="first",
            depends_on=["second"],
        ),
        AgentTask(
            id="second",
            agent="support",
            capability="support.run",
            objective="second",
            depends_on=["first"],
        ),
    )
    try:
        validate_plan(cyclic)
    except PlanValidationError as exc:
        assert "cycle" in str(exc)
    else:
        raise AssertionError("cyclic plan must be rejected")

    mismatched = _plan(
        AgentTask(
            id="wrong",
            agent="trade",
            capability="recommend.run",
            objective="wrong capability",
        )
    )
    try:
        validate_plan(mismatched)
    except PlanValidationError as exc:
        assert "does not belong" in str(exc)
    else:
        raise AssertionError("cross-agent capability must be rejected")


def test_executor_passes_upstream_artifact_to_dependent_agent() -> None:
    received_messages: list[str] = []
    received_artifacts: list[dict[str, object]] = []

    def recommend_worker(state: dict[str, object]) -> dict[str, object]:
        return {
            "response": "candidate",
            "references": [
                {"id": "package-42", "source": "food_package_index"}
            ],
        }

    def trade_worker(state: dict[str, object]) -> dict[str, object]:
        received_messages.append(str(state["message"]))
        received_artifacts.append(dict(state["upstream_artifacts"]))
        return {"response": "stock=8", "references": []}

    registry = {
        "recommend.run": CapabilitySpec("recommend", recommend_worker),
        "trade.query": CapabilitySpec("trade", trade_worker),
    }
    plan = _plan(
        AgentTask(
            id="recommend",
            agent="recommend",
            capability="recommend.run",
            objective="recommend a package",
        ),
        AgentTask(
            id="trade",
            agent="trade",
            capability="trade.query",
            objective="verify stock",
            depends_on=["recommend"],
        ),
    )

    result = _execute_read_plan(plan, registry)

    assert result["execution_status"] == "completed"
    assert received_messages == ["verify stock"]
    assert received_artifacts[0]["recommend"]["package_ids"] == [42]
    assert result["task_results"][0]["artifact"]["package_ids"] == [42]
    assert [item["status"] for item in result["task_results"]] == [
        "succeeded",
        "succeeded",
    ]


def test_executor_skips_dependents_after_required_failure() -> None:
    downstream_calls = 0

    def broken_worker(state: dict[str, object]) -> dict[str, object]:
        raise RuntimeError("unavailable")

    def downstream_worker(state: dict[str, object]) -> dict[str, object]:
        nonlocal downstream_calls
        downstream_calls += 1
        return {"response": "must not run"}

    registry = {
        "recommend.run": CapabilitySpec("recommend", broken_worker),
        "trade.query": CapabilitySpec("trade", downstream_worker),
    }
    plan = _plan(
        AgentTask(
            id="recommend",
            agent="recommend",
            capability="recommend.run",
            objective="recommend",
        ),
        AgentTask(
            id="trade",
            agent="trade",
            capability="trade.query",
            objective="query",
            depends_on=["recommend"],
        ),
    )

    result = _execute_read_plan(plan, registry)

    assert result["execution_status"] == "failed"
    assert downstream_calls == 0
    assert [item["status"] for item in result["task_results"]] == ["failed", "skipped"]


def test_executor_applies_each_task_timeout_independently() -> None:
    def short_timeout_worker(state: dict[str, object]) -> dict[str, object]:
        sleep(0.05)
        return {"response": "too late"}

    def healthy_worker(state: dict[str, object]) -> dict[str, object]:
        sleep(0.06)
        return {"response": "completed"}

    registry = {
        "recommend.run": CapabilitySpec("recommend", short_timeout_worker),
        "trade.query": CapabilitySpec("trade", healthy_worker),
    }
    plan = _plan(
        AgentTask(
            id="short",
            agent="recommend",
            capability="recommend.run",
            objective="must time out",
            timeout_seconds=0.01,
        ),
        AgentTask(
            id="healthy",
            agent="trade",
            capability="trade.query",
            objective="must complete",
            timeout_seconds=0.2,
        ),
    )

    result = _execute_read_plan(plan, registry)

    assert [item["status"] for item in result["task_results"]] == ["failed", "succeeded"]
    assert "timed out" in result["task_results"][0]["error"]
    assert result["execution_status"] == "partial"


def test_executor_applies_one_bounded_replan() -> None:
    calls = {"broken": 0, "replacement": 0}

    def broken_worker(state: dict[str, object]) -> dict[str, object]:
        calls["broken"] += 1
        raise RuntimeError("failed")

    def replacement_worker(state: dict[str, object]) -> dict[str, object]:
        calls["replacement"] += 1
        return {"response": "recovered"}

    registry = {
        "recommend.run": CapabilitySpec("recommend", broken_worker),
        "general.respond": CapabilitySpec("general", replacement_worker),
    }
    initial = _plan(
        AgentTask(
            id="broken",
            agent="recommend",
            capability="recommend.run",
            objective="broken",
        )
    )

    def replan(plan: ExecutionPlan, results: list[object]) -> ExecutionPlan:
        return _plan(
            AgentTask(
                id="replacement",
                agent="general",
                capability="general.respond",
                objective="recover",
            ),
            revision=2,
        )

    result = _execute_read_plan(initial, registry, max_replans=1, replanner=replan)

    assert result["execution_status"] == "completed"
    assert result["plan_revision"] == 2
    assert result["replan_count"] == 1
    assert calls == {"broken": 1, "replacement": 1}

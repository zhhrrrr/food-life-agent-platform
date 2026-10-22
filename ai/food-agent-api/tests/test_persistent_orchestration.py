from __future__ import annotations

from pathlib import Path
from threading import Event
from time import monotonic, sleep

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import app
from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.planner import DynamicPlanner
from app.orchestration.registry import CapabilitySpec, controlled_trade_node
from app.orchestration.service import OrchestrationService
from app.trade.actions import ActionResult
from app.trade.gateway_client import GatewayQueryResult
from app.trade.intent import TradeIntent
from app.trade.service import TradeWorkflowService


class FixedPlanner:
    def __init__(self, tasks: list[AgentTask]):
        self.tasks = tasks

    def plan(self, message: str, history=None) -> ExecutionPlan:
        return ExecutionPlan(
            run_id="planner-run",
            goal=message,
            tasks=self.tasks,
            confidence=1,
            reason="test plan",
            source="rules",
        )

    def replan(self, plan, results):
        return None


class Reads:
    def get_package_snapshot(self, package_id):
        return GatewayQueryResult(
            capability="package_snapshot",
            status="success",
            data={"packageId": package_id, "packageName": "双人餐", "packageStatus": 1, "price": 1800, "stock": 20},
        )

    def get_order(self, order_id, authorization):
        return GatewayQueryResult(
            capability="order_query",
            status="success",
            data={"order": {"id": order_id, "orderStatus": "WAIT_PAY", "payAmount": 3600}},
        )

    def list_orders(self, authorization):
        return GatewayQueryResult(capability="order_query", status="success", data={"orders": []})


class Actions:
    def __init__(self):
        self.calls: list[tuple] = []

    def create(self, args, context):
        self.calls.append(("create", args, context))
        return ActionResult(status="success", data={"orderId": 42, "orderStatus": "WAIT_PAY"})

    def pay(self, args, context):
        self.calls.append(("pay", args, context))
        return ActionResult(status="success", data={"payOrderNo": "P42"})

    def cancel(self, args, context):
        self.calls.append(("cancel", args, context))
        return ActionResult(status="success", data={"orderId": args.order_id, "orderStatus": "CANCELED"})

    def refund(self, args, context):
        self.calls.append(("refund", args, context))
        return ActionResult(status="success", data={"orderId": args.order_id, "refundStatus": "APPLIED"})


def settings(tmp_path: Path) -> Settings:
    return Settings(
        llm_router_enabled=False,
        long_term_memory_enabled=False,
        orchestration_checkpoint_path=str(tmp_path / "agent.sqlite"),
        trade_checkpoint_path=str(tmp_path / "trade.sqlite"),
    )


def workflow_task(*, trade_type="NORMAL", package_id=1, depends_on=None) -> AgentTask:
    return AgentTask(
        id="trade_transaction",
        agent="trade",
        capability="trade.workflow",
        objective="完成购买",
        depends_on=depends_on or [],
        trade_intent=TradeIntent(
            action="create",
            trade_type=trade_type,
            package_id=package_id,
            quantity=2 if trade_type == "NORMAL" else 1,
        ),
    )


def services(tmp_path: Path, tasks: list[AgentTask], registry: dict[str, CapabilitySpec] | None = None):
    config = settings(tmp_path)
    reads, actions = Reads(), Actions()
    child = TradeWorkflowService(config, read_client=reads, action_client=actions)
    capabilities = registry or {
        "trade.workflow": CapabilitySpec("trade", controlled_trade_node, execution="workflow"),
    }
    parent = OrchestrationService(config, planner=FixedPlanner(tasks), registry=capabilities, trade_service=child)
    return parent, child, reads, actions


def start(parent: OrchestrationService, request_id="start"):
    return parent.turn(
        user_id="7",
        authorization="Bearer token",
        session_id="session",
        request_id=request_id,
        message="请完成购买",
    )


def confirm(parent: OrchestrationService, current: dict, request_id="confirm"):
    return parent.turn(
        user_id="7",
        authorization="Bearer token",
        session_id="session",
        run_id=current["run_id"],
        task_id=current["interrupt"]["task_id"],
        request_id=request_id,
        message="确认",
        confirmation_id=current["trade"]["interrupt"]["confirmation_id"],
        approved=True,
    )


def test_parent_checkpoint_resumes_confirmed_child_once(tmp_path):
    parent, child, _, actions = services(tmp_path, [workflow_task()])
    try:
        pending = start(parent)
        assert pending["execution_status"] == "waiting_input"
        assert pending["interrupt"]["type"] == "trade"
        completed = confirm(parent, pending)
        assert completed["execution_status"] == "completed"
        assert completed["trade"]["status"] == "completed"
        assert len(actions.calls) == 1
        assert confirm(parent, pending) == completed
        assert len(actions.calls) == 1
        assert {event["event"] for event in parent.events("7", pending["run_id"])} >= {
            "plan_ready", "trade_state", "snapshot", "turn_finished"
        }
    finally:
        parent.close()
        child.close()


def test_parent_and_child_interrupt_survive_process_restart(tmp_path):
    config = settings(tmp_path)
    parent, child, reads, actions = services(tmp_path, [workflow_task()])
    pending = start(parent)
    parent.close()
    child.close()

    restarted_child = TradeWorkflowService(config, read_client=reads, action_client=actions)
    restarted_parent = OrchestrationService(
        config,
        planner=FixedPlanner([workflow_task()]),
        registry={"trade.workflow": CapabilitySpec("trade", controlled_trade_node, execution="workflow")},
        trade_service=restarted_child,
    )
    try:
        restored = restarted_parent.view("7", pending["run_id"], "session")
        assert restored["trade"]["interrupt"] == pending["trade"]["interrupt"]
        assert confirm(restarted_parent, restored)["trade"]["status"] == "completed"
        assert len(actions.calls) == 1
    finally:
        restarted_parent.close()
        restarted_child.close()


def test_composite_dag_reads_before_controlled_transaction(tmp_path):
    calls: list[str] = []

    def recommend(_):
        calls.append("recommend")
        return {"response": "候选套餐1", "references": [{"id": "package-1", "source": "test"}]}

    def support(_):
        calls.append("support")
        return {"response": "拼团要等待成团", "references": []}

    tasks = [
        AgentTask(id="recommend_candidates", agent="recommend", capability="recommend.run", objective="推荐"),
        AgentTask(id="support_policy", agent="support", capability="support.run", objective="规则"),
        workflow_task(package_id=None, depends_on=["recommend_candidates", "support_policy"]),
    ]
    registry = {
        "recommend.run": CapabilitySpec("recommend", recommend),
        "support.run": CapabilitySpec("support", support),
        "trade.workflow": CapabilitySpec("trade", controlled_trade_node, execution="workflow"),
    }
    parent, child, _, actions = services(tmp_path, tasks, registry)
    try:
        pending = start(parent)
        assert sorted(calls) == ["recommend", "support"]
        assert pending["agents"] == ["recommend", "support", "trade"]
        assert pending["trade"]["interrupt"]["preview"]["package_id"] == 1
        assert confirm(parent, pending)["execution_status"] == "completed"
        assert len(actions.calls) == 1
    finally:
        parent.close()
        child.close()


def test_purchase_option_selection_precedes_trade_confirmation(tmp_path):
    options = [
        {"trade_type": "NORMAL", "price_cents": 1800, "message": "普通下单套餐1，数量1", "conditions": "实时价"},
        {"trade_type": "GROUP_BUY", "price_cents": 1500, "message": "开团套餐1，数量1", "conditions": "等待成团"},
    ]

    def advise(_):
        return {"response": "已比较购买方式", "purchase_options": options, "facts": []}

    tasks = [
        AgentTask(
            id="trade_options",
            agent="trade",
            capability="trade.advise",
            objective="比较方案",
            trade_intent=TradeIntent(action="advise", package_id=1, quantity=1),
        ),
        workflow_task(trade_type=None, package_id=1, depends_on=["trade_options"]),
    ]
    registry = {
        "trade.advise": CapabilitySpec("trade", advise),
        "trade.workflow": CapabilitySpec("trade", controlled_trade_node, execution="workflow"),
    }
    parent, child, _, actions = services(tmp_path, tasks, registry)
    try:
        selection = start(parent)
        assert selection["interrupt"]["type"] == "selection"
        assert [choice["message"] for choice in selection["interrupt"]["choices"]] == ["方案1", "方案2"]
        pending = parent.turn(
            user_id="7", authorization="Bearer token", session_id="session", run_id=selection["run_id"],
            task_id="trade_transaction", request_id="select", message="方案1",
        )
        assert pending["execution_status"] == "waiting_input"
        assert pending["trade"]["interrupt"]["type"] == "confirmation"
        assert pending["trade"]["interrupt"]["preview"]["trade_type"] == "NORMAL"
        assert confirm(parent, pending)["execution_status"] == "completed"
        assert len(actions.calls) == 1
    finally:
        parent.close()
        child.close()


def test_rules_planner_preserves_multi_agent_purchase_dag():
    planner = DynamicPlanner(settings=Settings(llm_router_enabled=False))
    plan = planner.plan("推荐适合两人的套餐，说明秒杀和拼团规则，比较普通、拼团和秒杀，合适就帮我买")
    capabilities = [task.capability for task in plan.tasks]
    assert "recommend.run" in capabilities
    assert "support.run" in capabilities
    assert "trade.advise" in capabilities
    assert capabilities[-1] == "trade.workflow"
    assert set(plan.tasks[-1].depends_on) == {task.id for task in plan.tasks[:-1]}


def test_rules_question_does_not_trigger_transaction():
    plan = DynamicPlanner(settings=Settings(llm_router_enabled=False)).plan("秒杀有什么规则")
    assert all(task.capability != "trade.workflow" for task in plan.tasks)
    assert any(task.capability == "support.run" for task in plan.tasks)


def test_async_run_pauses_at_checkpoint_and_resumes_without_repeating_task(tmp_path):
    entered, release = Event(), Event()
    calls: list[str] = []

    def first(_):
        calls.append("first")
        entered.set()
        assert release.wait(5)
        return {"response": "first complete"}

    def second(_):
        calls.append("second")
        return {"response": "second complete"}

    tasks = [
        AgentTask(id="first", agent="recommend", capability="recommend.run", objective="first"),
        AgentTask(id="second", agent="support", capability="support.run", objective="second", depends_on=["first"]),
    ]
    parent = OrchestrationService(settings(tmp_path), planner=FixedPlanner(tasks), registry={
        "recommend.run": CapabilitySpec("recommend", first),
        "support.run": CapabilitySpec("support", second),
    })
    try:
        accepted = parent.submit(user_id="7", authorization="Bearer token", session_id="session",
                                 request_id="async-start", message="long task")
        assert entered.wait(5)
        running = next(iter(parent.futures.values()))
        requested = parent.pause("7", accepted["run_id"])
        assert requested["execution_status"] in {"pausing", "paused"}
        release.set()
        deadline = monotonic() + 5
        while monotonic() < deadline:
            current = parent.view("7", accepted["run_id"], "session")
            if current["execution_status"] == "paused":
                break
            sleep(0.02)
        assert current["execution_status"] == "paused"
        assert calls == ["first"]
        # The paused checkpoint is visible before the async turn releases its database lease.
        running.result(timeout=5)
        completed = parent.turn(user_id="7", authorization="Bearer token", session_id="session",
                                run_id=accepted["run_id"], request_id="resume", recover=True)
        assert completed["execution_status"] == "completed"
        assert calls == ["first", "second"]
    finally:
        release.set()
        parent.close()


def test_sse_endpoint_replays_durable_events_and_checks_owner(tmp_path, monkeypatch):
    task = AgentTask(id="general", agent="general", capability="general.respond", objective="hello")
    parent = OrchestrationService(settings(tmp_path), planner=FixedPlanner([task]), registry={
        "general.respond": CapabilitySpec("general", lambda _: {"response": "done"}),
    })
    try:
        monkeypatch.setattr("app.api.runs.get_orchestration_service", lambda: parent)
        class Memory:
            settings = Settings(session_memory_max_turns=8)

            def load(self, *args, **kwargs):
                return type("Context", (), {"history": lambda self, limit: []})(), "test"

            def append(self, *args, **kwargs):
                return "test"

        monkeypatch.setattr("app.api.runs.session_memory", Memory())
        client = TestClient(app)
        headers = {"x-user-id": "7", "x-internal-secret": "test-gateway-secret", "Authorization": "Bearer token"}
        accepted = client.post("/api/agent/runs", json={"session_id": "session", "request_id": "event-request", "message": "hello"}, headers=headers)
        assert accepted.status_code == 202
        run_id = accepted.json()["run_id"]
        response = client.get(f"/api/agent/runs/{run_id}/events?request_id=event-request", headers=headers)
        assert response.status_code == 200
        assert "event: plan_ready" in response.text
        assert "event: turn_finished" in response.text
        assert client.get(f"/api/agent/runs/{run_id}", headers=headers).json()["execution_status"] == "completed"
        with pytest.raises(HTTPException) as exc:
            parent.events("8", run_id)
        assert exc.value.status_code == 404
    finally:
        parent.close()

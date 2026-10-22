import time
from types import SimpleNamespace
from pathlib import Path

import httpx
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import app
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.trade.actions import ActionResult, TradeActionClient, build_action_registry
from app.trade.gateway_client import GatewayQueryResult
from app.trade.intent import parse_intent
from app.trade.service import TradeWorkflowService
from app.orchestration.service import OrchestrationService


class Reads:
    price = 1800
    order_status = "WAIT_PAY"

    def get_package_snapshot(self, package_id):
        return GatewayQueryResult(capability="package_snapshot", status="success", data={"packageId": package_id, "price": self.price, "stock": 20})

    def get_order(self, order_id, authorization):
        return GatewayQueryResult(capability="order_query", status="success", data={"order": {"id": order_id, "orderStatus": self.order_status, "payAmount": 3600}})

    def list_orders(self, authorization):
        return GatewayQueryResult(capability="order_query", status="success", data={"orders": [{"orderId": 42}]})


class Actions:
    def __init__(self):
        self.calls = []
        self.result = ActionResult(status="success", data={"orderId": 42, "orderStatus": "WAIT_PAY"})
        self.pay_result = None

    def create(self, args, context):
        self.calls.append(("create", args, context))
        return self.result

    def pay(self, args, context):
        self.calls.append(("pay", args, context))
        return self.pay_result or ActionResult(status="success", data={"payOrderNo": "P42", "channel": "ALIPAY_SANDBOX", "payStatus": "PREPARED", "paymentForm": "<form></form>"})

    def cancel(self, args, context):
        self.calls.append(("cancel", args, context))
        return ActionResult(status="success", data={"orderId": args.order_id, "orderStatus": "CANCELED"})

    def refund(self, args, context):
        self.calls.append(("refund", args, context))
        return ActionResult(status="success", data={"orderId": args.order_id, "refundStatus": "APPLIED"})


@pytest.fixture
def workflow(tmp_path):
    settings = Settings(trade_checkpoint_path=str(tmp_path / "trade.sqlite"))
    reads, actions = Reads(), Actions()
    service = TradeWorkflowService(settings, read_client=reads, action_client=actions)
    yield service, reads, actions
    service.close()


def turn(service, message="", **kwargs):
    return service.turn(user_id="7", authorization="Bearer test-token", session_id="session", message=message, **kwargs)


def confirm(service, result, approved=True, **kwargs):
    return turn(service, thread_id=result["thread_id"], confirmation_id=result["interrupt"]["confirmation_id"], approved=approved, **kwargs)


def test_multiturn_parameters_confirm_and_idempotency(workflow):
    service, _, actions = workflow
    first = turn(service, "帮我下单", request_id="start")
    assert first["status"] == "awaiting_parameters"
    second = turn(service, "套餐1", thread_id=first["thread_id"])
    assert second["interrupt"]["missing_fields"] == ["quantity"]
    third = turn(service, "2份", thread_id=first["thread_id"])
    assert third["status"] == "awaiting_confirmation"
    assert third["interrupt"]["preview"]["estimated_total_cents"] == 3600
    assert not actions.calls
    finished = confirm(service, third, request_id="confirm")
    assert finished["status"] == "completed"
    assert len(actions.calls) == 1
    assert confirm(service, third, request_id="confirm") == finished
    assert confirm(service, third)["status"] == "completed"
    assert len(actions.calls) == 1


def test_restart_restores_interrupt_without_storing_authorization(workflow):
    service, reads, actions = workflow
    first = turn(service, "下单套餐1，2份")
    restarted = TradeWorkflowService(service.settings, read_client=reads, action_client=actions)
    try:
        assert restarted.view("7", first["thread_id"])["interrupt"] == first["interrupt"]
        assert confirm(restarted, first)["status"] == "completed"
        with service.storage.connect() as db:
            rows = db.execute("SELECT checkpoint,metadata FROM checkpoints").fetchall()
        assert all(b"test-token" not in bytes(value) for row in rows for value in row if value)
    finally:
        restarted.close()


def test_response_loss_after_checkpoint_returns_same_workflow(workflow, monkeypatch):
    service, _, actions = workflow
    remember = service.storage.remember

    def crash(*args):
        raise RuntimeError("crash after checkpoint, before HTTP receipt")

    monkeypatch.setattr(service.storage, "remember", crash)
    with pytest.raises(RuntimeError):
        turn(service, "下单套餐1，2份", request_id="durable-request")
    monkeypatch.setattr(service.storage, "remember", remember)
    ready = turn(service, "下单套餐1，2份", request_id="durable-request")
    assert ready["status"] == "awaiting_confirmation"
    assert not actions.calls
    monkeypatch.setattr(service.storage, "remember", crash)
    with pytest.raises(RuntimeError):
        confirm(service, ready, request_id="durable-confirmation")
    monkeypatch.setattr(service.storage, "remember", remember)
    assert confirm(service, ready, request_id="durable-confirmation")["status"] == "completed"
    assert len(actions.calls) == 1


@pytest.mark.parametrize("approved,status", [(False, "rejected"), (True, "completed")])
def test_sensitive_decisions(workflow, approved, status):
    service, _, actions = workflow
    first = turn(service, "取消订单42")
    assert confirm(service, first, approved)["status"] == status
    assert len(actions.calls) == int(approved)


def test_expired_wrong_confirmation_and_changed_price(workflow, monkeypatch):
    service, reads, actions = workflow
    first = turn(service, "下单套餐1，2份")
    with pytest.raises(HTTPException) as error:
        turn(service, thread_id=first["thread_id"], confirmation_id="0" * 32, approved=True)
    assert error.value.status_code == 409
    future = time.time() + 600
    monkeypatch.setattr("app.trade.workflow.time.time", lambda: future)
    assert confirm(service, first)["status"] == "expired"
    monkeypatch.undo()
    assert not actions.calls
    second = turn(service, "下单套餐1，2份")
    reads.price += 1
    assert confirm(service, second)["status"] == "stale"
    assert not actions.calls


def test_uncertain_write_explicit_recovery_reuses_operation_id(workflow):
    service, _, actions = workflow
    actions.result = ActionResult(status="uncertain", message="timeout")
    first = turn(service, "下单套餐1，2份")
    assert confirm(service, first)["status"] == "uncertain"
    actions.result = ActionResult(status="success", data={"orderId": 42})
    result = turn(service, thread_id=first["thread_id"], recover=True)
    assert result["status"] == "completed"
    assert actions.calls[0][2].operation_id == actions.calls[1][2].operation_id


def test_query_pay_refund_and_state_gates(workflow):
    service, reads, actions = workflow
    query = turn(service, "查询订单42")
    assert query["result"]["data"]["order"]["id"] == 42
    assert "WAIT_PAY" in query["response"] and "3600" in query["response"]
    payment = turn(service, "支付订单42")
    paid = confirm(service, payment)
    assert paid["result"]["data"]["payStatus"] == "PREPARED"
    assert "支付状态以服务端" in paid["response"]
    assert turn(service, "申请退款订单42，原因：不需要了")["status"] == "failed"
    reads.order_status = "PAID"
    refund = turn(service, "申请退款订单42")
    assert refund["interrupt"]["missing_fields"] == ["refund_reason"]
    ready = turn(service, "原因：计划改变", thread_id=refund["thread_id"])
    assert confirm(service, ready)["status"] == "completed"
    assert actions.calls[-1][1].refund_reason == "计划改变"


def test_payment_recovery_reconciles_paid_order_without_new_checkout(workflow):
    service, reads, actions = workflow
    actions.pay_result = ActionResult(status="uncertain", message="checkout response lost")
    ready = turn(service, "支付订单42")
    assert confirm(service, ready)["status"] == "uncertain"
    reads.order_status = "PAID"
    recovered = turn(service, thread_id=ready["thread_id"], recover=True)
    assert recovered["status"] == "completed"
    assert recovered["result"]["data"]["payStatus"] == "SUCCESS"
    assert len(actions.calls) == 1


def test_isolation_request_key_conflict_and_concurrency(workflow):
    service, _, actions = workflow
    first = turn(service, "下单套餐1，2份", request_id="start")
    with pytest.raises(HTTPException) as error:
        service.view("8", first["thread_id"])
    assert error.value.status_code == 404
    with pytest.raises(HTTPException):
        turn(service, "下单套餐2，3份", thread_id=first["thread_id"], request_id="start")
    with service.storage.lease(service.config("7", first["thread_id"])["configurable"]["thread_id"]):
        with pytest.raises(HTTPException) as error:
            confirm(service, first)
    assert error.value.status_code == 409
    assert not actions.calls


def test_write_tools_require_exact_approval(workflow):
    _, _, actions = workflow
    executor = ToolExecutor(build_action_registry(actions))
    call = ToolCall(name="trade_cancel_order", arguments={"order_id": 42})
    assert executor.execute([call], ToolContext(authorization="token", allowed_tool_names=frozenset({call.name})))[0].status == "rejected"
    altered = ToolCall(name="trade_cancel_order", arguments={"order_id": 43})
    assert executor.execute([altered], ToolContext(authorization="token", approved_call=call, operation_id="op", allowed_tool_names=frozenset({call.name})))[0].status == "rejected"
    assert not actions.calls


def test_llm_extracts_intent_but_does_not_repeat_write_proposal(workflow):
    service, reads, actions = workflow

    class Model:
        binds = 0

        def with_structured_output(self, schema, **kwargs):
            return SimpleNamespace(invoke=lambda messages: schema(action="create", package_id=1, quantity=2))

        def bind_tools(self, schemas):
            self.binds += 1
            raise AssertionError("write proposals are generated by application code")

    settings = service.settings.model_copy(update={"llm_router_enabled": True})
    model = Model()
    assert parse_intent("买两份一号套餐", {}, settings, model).quantity == 2
    llm_service = TradeWorkflowService(settings, read_client=reads, action_client=actions, chat_model=model)
    try:
        ready = turn(llm_service, "下单套餐1，2份")
        assert ready["interrupt"]["tool"]["name"] == "trade_create_order"
        assert confirm(llm_service, ready)["status"] == "completed"
        assert actions.calls[-1][0] == "create"
        assert model.binds == 0
    finally:
        llm_service.close()


def test_gateway_contract_and_timeout(monkeypatch):
    posts = []

    def post(url, **kwargs):
        posts.append((url, kwargs))
        return httpx.Response(200, json={"code": "0000", "data": {"orderId": 42}})

    monkeypatch.setattr("app.trade.actions.httpx.post", post)
    registry = build_action_registry(TradeActionClient(Settings()))
    call = ToolCall(name="trade_create_order", arguments={"package_id": 1, "quantity": 2})
    result = ToolExecutor(registry).execute([call], ToolContext(authorization="token", approved_call=call, operation_id="same-op", allowed_tool_names=frozenset({call.name})))[0]
    assert result.result.status == "success"
    assert posts[0][1]["json"]["requestId"] == "same-op"
    assert posts[0][1]["headers"]["Authorization"] == "token"
    assert posts[0][0].endswith("/api/trade/orders/normal")

    def timeout(*args, **kwargs):
        raise httpx.ReadTimeout("lost response")

    monkeypatch.setattr("app.trade.actions.httpx.post", timeout)
    result = ToolExecutor(registry).execute([call], ToolContext(authorization="token", approved_call=call, operation_id="same-op", allowed_tool_names=frozenset({call.name})))[0]
    assert result.result.status == "uncertain"


def test_api_and_main_chat_use_trusted_gateway_and_workflow(workflow, monkeypatch):
    service, _, actions = workflow
    parent = OrchestrationService(service.settings.model_copy(update={
        "orchestration_checkpoint_path": str(Path(service.settings.trade_checkpoint_path).with_name("agent.sqlite")),
    }), trade_service=service)
    monkeypatch.setattr("app.api.trade.get_trade_service", lambda: service)
    monkeypatch.setattr("app.api.chat.get_trade_service", lambda: service)
    monkeypatch.setattr("app.orchestration.service.get_orchestration_service", lambda: parent)
    client = TestClient(app)
    try:
        assert client.post("/api/agent/trade/chat", json={"message": "下单套餐1，2份"}, headers={"x-user-id": "7", "Authorization": "token"}).status_code == 401
        headers = {"x-user-id": "7", "x-internal-secret": "test-gateway-secret", "Authorization": "token"}
        response = client.post("/api/agent/chat", json={"message": "下单套餐1，2份", "session_id": "session", "request_id": "parent-start"}, headers=headers)
        assert response.status_code == 200
        result = response.json()
        assert result["workflow"] == "trade_only"
        assert result["execution_status"] == "waiting_input"
        trade = result["trade"]
        assert client.get("/api/agent/trade/threads/" + trade["thread_id"], headers=headers).status_code == 200
        response = client.post("/api/agent/chat", json={"message": "确认", "session_id": "session", "run_id": result["run_id"],
            "task_id": result["interrupt"]["task_id"], "request_id": "parent-confirm",
            "confirmation_id": trade["interrupt"]["confirmation_id"], "approved": True}, headers=headers)
        assert response.status_code == 200
        assert response.json()["trade"]["status"] == "completed"
        assert len(actions.calls) == 1
    finally:
        parent.close()

import json

import pytest

from app.agents.general_agent import GeneralAnswer, general_agent_node
from app.agents.trade_agent import trade_advise_node, trade_agent_node
from app.core.config import Settings
from app.llm.structured_output import invoke_structured_output
from app.orchestration.models import TaskArtifact
from app.orchestration.planner import DynamicPlanner
from app.trade.gateway_client import GatewayQueryResult
from app.trade.intent import parse_intent


@pytest.mark.parametrize("text,expected", [
    ("十二份", 12), ("二十份", 20), ("二十三份", 23), ("三份", 3),
    ("数量：12", 12), ("数量为2", 2), ("份数2", 2), ("买两个套餐42", 2),
    ("数量一共2份", 2), ("买42元的套餐", None), ("1.5份", None),
    ("-2份", None), ("负两份", None), ("一百二十份", None), ("100份", None),
])
def test_quantity_followup_is_grounded_and_not_truncated(text, expected):
    previous = {"action": "create", "package_id": 42, "trade_type": "NORMAL"}
    assert parse_intent(text, previous, Settings(llm_router_enabled=False)).quantity == expected


@pytest.mark.parametrize("text,expected", [("三份", 3), ("-2份", None), ("1.5份", None), ("2份或3份", None)])
def test_current_quantity_correction_overrides_previous_value(text, expected):
    previous = {"action": "create", "package_id": 42, "quantity": 1, "trade_type": "NORMAL"}
    assert parse_intent(text, previous, Settings(llm_router_enabled=False)).quantity == expected


def test_model_cannot_supply_a_quantity_not_present_in_request():
    class Model:
        def with_structured_output(self, schema, **kwargs):
            return self

        def invoke(self, messages):
            return {"action": "create", "package_id": 42, "quantity": 42, "trade_type": "NORMAL"}

    intent = parse_intent("普通下单套餐42", {}, Settings(llm_router_enabled=True), Model())
    assert intent.quantity is None


def test_complete_purchase_request_with_ge_unit_is_recognized_without_history():
    intent = parse_intent("买两个套餐42", {}, Settings(llm_router_enabled=False))
    assert (intent.action, intent.package_id, intent.quantity) == ("create", 42, 2)


@pytest.mark.parametrize("previous,message,quantity", [
    ({"action": "create", "package_id": 42, "trade_type": "NORMAL"}, "12", 12),
    ({"action": "create", "trade_type": "NORMAL"}, "42", None),
    ({"action": "create", "package_id": 42, "quantity": 2, "trade_type": "NORMAL"}, "不用券", 2),
])
def test_quantity_followup_preserves_bare_number_and_unrelated_history(previous, message, quantity):
    assert parse_intent(message, previous, Settings(llm_router_enabled=False)).quantity == quantity


@pytest.mark.parametrize("provider,has_system", [("zhipu", True), ("zhipu", False), ("openai", True)])
def test_json_mode_schema_is_centralized_without_mutating_messages(provider, has_system):
    messages = [("system", "Be concise.")] if has_system else []
    messages.append(("human", "a question"))
    original = list(messages)

    class Model:
        def with_structured_output(self, schema, **kwargs):
            return self

        def invoke(self, supplied):
            assert supplied[-1] == original[-1]
            if provider == "zhipu":
                assert supplied[0][0] == "system"
                assert json.loads(supplied[0][1].split("\nJSON Schema:\n", 1)[1]) == GeneralAnswer.model_json_schema()
            else:
                assert supplied == original
            return {"response_type": "chat", "response": "done"}

    for _ in range(2):
        invoke_structured_output(settings=Settings(llm_provider=provider), schema=GeneralAnswer,
                                 messages=messages, chat_model=Model())
        assert messages == original


@pytest.mark.parametrize("message,kind", [("你好！", "chat"), ("谢谢", "chat"),
    ("你好，你能做什么？", "capability_guide"), ("你是谁", "capability_guide")])
def test_simple_general_request_skips_model_in_both_planner_and_agent(monkeypatch, message, kind):
    def forbidden(**kwargs):
        pytest.fail("simple receptionist requests should not need a model")

    monkeypatch.setattr("app.agents.general_agent.invoke_structured_output_with_fallback", forbidden)
    monkeypatch.setattr("app.orchestration.planner.invoke_structured_output", forbidden)
    assert DynamicPlanner(Settings(llm_router_enabled=True)).plan(message).source == "rules"
    result = general_agent_node({"message": message})
    assert result["rag_mode"] == f"general_agent_{kind}"


def test_general_fallback_does_not_match_hi_inside_other_words(monkeypatch):
    monkeypatch.setattr("app.agents.general_agent.get_settings", lambda: Settings(llm_router_enabled=False))
    assert general_agent_node({"message": "Which planet is largest?"})["rag_mode"] == "general_agent_out_of_scope"


def test_structured_trade_query_returns_facts_and_typed_reference(monkeypatch):
    result = GatewayQueryResult(capability="order_query", status="success", reference_id="order:42",
        reference_title="订单42", data={"order": {"orderId": 42, "status": "PAID", "payAmount": 9900}})
    monkeypatch.setattr("app.trade.purchase.PurchaseReader.query", lambda *args: result)
    payload = trade_agent_node({"message": "查询订单42", "trade_intent": {"action": "query", "order_id": 42}})
    assert "PAID" in payload["response"] and "9900" in payload["response"]
    artifact = TaskArtifact.from_payload(payload)
    assert artifact.order_ids == [42]
    assert artifact.references[0]["id"] == "order:42"
    assert artifact.facts[0]["data"] == result.data


def test_structured_trade_query_failure_has_no_success_facts(monkeypatch):
    result = GatewayQueryResult(capability="order_query", status="unavailable", message="稍后重试")
    monkeypatch.setattr("app.trade.purchase.PurchaseReader.query", lambda *args: result)
    payload = trade_agent_node({"message": "查询订单42", "trade_intent": {"action": "query", "order_id": 42}})
    assert "稍后重试" in payload["response"]
    assert not payload["facts"] and not payload["references"]


def test_structured_advice_reports_recommendation_price_and_conditions(monkeypatch):
    option = {"trade_type": "GROUP_BUY", "price_cents": 9900, "conditions": "需要等待成团"}
    result = GatewayQueryResult(capability="group_buy", status="success", reference_id="package:42",
        reference_title="拼团价", data={"packageId": 42, "groupPrice": 9900})
    data = {"recommended": option, "options": [option], "facts": {"group": result.model_dump()}, "note": "未创建订单。"}
    monkeypatch.setattr("app.trade.purchase.PurchaseReader.advise", lambda *args: data)
    payload = trade_advise_node({"trade_intent": {"action": "advise", "package_id": 42}})
    assert all(text in payload["response"] for text in ("拼团购买", "99.00", "等待成团", "未创建订单"))
    assert TaskArtifact.from_payload(payload).package_ids == [42]
    assert payload["purchase_options"] == [option]

"""Planner boundaries; model doubles replace only the external LLM call."""
from __future__ import annotations

import json
import pytest

from app.core.config import Settings
from app.memory.session_memory import SessionTurn
from app.orchestration.models import AgentTask, ExecutionPlan, PlanDraft, TaskArtifact, TaskResult
from app.orchestration.planner import DynamicPlanner
from app.orchestration.validator import PlanValidationError, validate_plan
from app.trade.intent import TradeIntent


class DraftModel:
    def __init__(self, tasks):
        self.tasks = tasks

    def with_structured_output(self, schema, **kwargs):
        class Structured:
            def invoke(inner, messages):
                return {"tasks": self.tasks, "confidence": 0.9, "reason": "test"}
        return Structured()


def planner(*tasks):
    return DynamicPlanner(
        Settings(llm_router_enabled=True, semantic_router_enabled=False),
        chat_model=DraftModel(list(tasks)),
    )


def task(identifier, agent, capability, objective, **kwargs):
    return dict(id=identifier, agent=agent, capability=capability,
                objective=objective, depends_on=[], **kwargs)


@pytest.mark.parametrize(("message", "capability", "draft", "expected"), [
    ("套餐1怎么买划算，预算150元，不想等成团", "trade.advise",
     {"action": "advise", "package_id": 1},
     {"budget_cents": 15000, "willing_to_wait": False}),
    ("套餐81哪种购买方式能马上确定？我不愿意等拼团。", "trade.advise",
     {"action": "advise", "package_id": 81, "willing_to_wait": True},
     {"willing_to_wait": False}),
    ("普通下单", "trade.workflow", {"action": "create"}, {"trade_type": "NORMAL"}),
    ("参团套餐1，团队GBT1，数量1", "trade.workflow",
     {"action": "create", "package_id": 1, "quantity": 1, "trade_type": "NORMAL"},
     {"trade_type": "GROUP_BUY", "group_mode": "join", "team_id": "GBT1"}),
])
def test_explicit_purchase_fields_survive_incomplete_model_plans(message, capability, draft, expected):
    plan = planner(task("purchase", "trade", capability, "处理购买请求", trade_intent=draft)).plan(message)
    assert plan.source == "llm"
    intent = next(item.trade_intent for item in plan.tasks if item.capability == capability)
    for key, value in expected.items():
        assert getattr(intent, key) == value


def test_explicit_payment_refresh_reaches_confirmed_sync_workflow():
    plan = planner(task("payment", "trade", "trade.query", "刷新支付结果",
                        trade_intent={"action": "query", "query_kind": "payment", "pay_order_no": "PAY123"})).plan(
        "刷新支付单PAY123的支付结果。")
    assert [item.capability for item in plan.tasks] == ["trade.workflow"]
    assert plan.tasks[0].trade_intent.action == "sync_payment"
    assert plan.tasks[0].trade_intent.pay_order_no == "PAY123"


def test_model_cannot_choose_open_group_when_user_did_not_choose_open_or_join():
    plan = planner(task("buy", "trade", "trade.workflow", "拼团购买",
                        trade_intent={"action": "create", "trade_type": "GROUP_BUY",
                                      "package_id": 1, "quantity": 1, "group_mode": "open"})).plan(
        "拼团下单套餐1，数量1")
    assert plan.tasks[0].trade_intent.group_mode is None


def test_recommended_package_stock_cannot_fall_through_to_order_list():
    plan = planner(
        task("candidates", "recommend", "recommend.run", "推荐双人套餐"),
        {**task("stock", "trade", "trade.query", "查询推荐套餐的实时库存",
                trade_intent={"action": "query"}), "depends_on": ["candidates"]},
    ).plan("推荐一个适合双人的套餐，再查询它的实时库存。")
    assert plan.source == "llm"
    assert plan.tasks[1].trade_intent is None
    assert plan.tasks[1].depends_on == ["candidates"]


@pytest.mark.parametrize("message", ["你好，先告诉我怎么核销", "Which payment methods are available?"])
def test_greeting_substrings_cannot_hide_business_requests(message):
    plan = planner(task("policy", "support", "support.run", message)).plan(message)
    assert plan.source == "llm"
    assert [t.capability for t in plan.tasks] == ["support.run"]


@pytest.mark.parametrize("message", ["你好", "你好，你是谁？", "Hi!", "谢谢"])
def test_only_complete_greetings_use_the_fast_path(message):
    plan = planner().plan(message)
    assert plan.source == "rules"
    assert [t.capability for t in plan.tasks] == ["general.respond"]


def test_explicit_query_uses_only_the_model_requested_capability():
    plan = planner(task("stock", "trade", "trade.query", "查询套餐42库存")).plan(
        "查询套餐42库存"
    )
    assert plan.source == "llm"
    assert [t.capability for t in plan.tasks] == ["trade.query"]


def test_rule_postprocessing_cannot_turn_a_negated_purchase_into_a_workflow():
    plan = planner(task("stock", "trade", "trade.query", "查询套餐42库存")).plan(
        "不要帮我下单，只查询套餐42库存"
    )
    assert [t.capability for t in plan.tasks] == ["trade.query"]


def test_model_dependencies_on_live_facts_survive_materialization():
    policy = task("policy", "support", "support.run", "根据实时库存解释售罄规则")
    policy["depends_on"] = ["stock"]
    plan = planner(
        task("stock", "trade", "trade.query", "查询套餐42库存"), policy,
    ).plan("查询套餐42库存，再根据库存解释售罄规则")
    assert plan.source == "llm"
    assert plan.tasks[1].depends_on == ["stock"]


def test_invalid_llm_plan_uses_business_fallback_instead_of_general():
    plan = planner(task("bad", "trade", "trade.delete_database", "查询订单123")).plan(
        "查询订单123"
    )
    assert plan.source == "rules"
    assert [t.capability for t in plan.tasks] == ["trade.query"]


def test_malformed_input_reference_is_rejected_instead_of_silently_removed():
    advice = task("advice", "trade", "trade.advise", "比较套餐42购买方式",
                  trade_intent={"action": "advise", "package_id": 42},
                  input_refs={"package_id": "missing_separator"})
    plan = planner(advice).plan("套餐42怎么买，哪个划算")
    assert plan.source == "rules"


@pytest.mark.parametrize("in_objective", [True, False])
def test_model_cannot_invent_package_ids(in_objective):
    candidate = task("stock", "trade", "trade.query",
                     "查询套餐999库存" if in_objective else "查询实时库存")
    if not in_objective:
        candidate["trade_intent"] = {"action": "query", "package_id": 999}
    plan = planner(candidate).plan("查询套餐42库存")
    assert plan.source == "rules"
    assert all("999" not in t.objective for t in plan.tasks)


def test_selected_history_can_supply_an_existing_package_id():
    plan = planner(task("stock", "trade", "trade.query", "查询套餐42库存")).plan(
        "那库存呢", history=[SessionTurn(user="查询套餐42价格", intent="trade")]
    )
    assert plan.source == "llm"
    assert plan.tasks[0].objective == "查询套餐42库存"


def test_replan_ids_must_be_grounded_in_the_goal_or_successful_artifacts():
    initial = ExecutionPlan(run_id="run-12345678", goal="推荐套餐并查询库存",
        source="rules", confidence=0.9, reason="test", tasks=[
            AgentTask(id="recommend", agent="recommend", capability="recommend.run", objective="推荐套餐")])
    p = planner(task("stock", "trade", "trade.query", "查询套餐42库存"))
    assert p.replan(initial, []) is None
    result = TaskResult(task_id="recommend", agent="recommend", status="succeeded",
                        artifact=TaskArtifact(package_ids=[42]))
    revised = p.replan(initial, [result])
    assert revised is not None
    assert revised.tasks[0].objective == "查询套餐42库存"


@pytest.mark.parametrize("field,reference,capability", [
    ("order_id", "producer.package_ids", "trade.workflow"),
    ("package_id", "producer.order_ids", "trade.workflow"),
    ("package_id", "producer.package_ids", "trade.query"),
])
def test_input_references_require_matching_id_types_and_a_consuming_adapter(field, reference, capability):
    plan = ExecutionPlan(run_id="run-12345678", goal="test", source="rules", confidence=1,
        reason="test", tasks=[
            AgentTask(id="producer", agent="recommend", capability="recommend.run", objective="推荐套餐"),
            AgentTask(id="consumer", agent="trade", capability=capability, objective="消费产物",
                depends_on=["producer"], input_refs={field: reference},
                trade_intent=TradeIntent(action="create" if capability == "trade.workflow" else "query")),
        ])
    with pytest.raises(PlanValidationError, match="input references"):
        validate_plan(plan)


def test_llm_failure_still_returns_a_valid_rule_plan():
    class UnavailableModel:
        def with_structured_output(self, schema, **kwargs):
            raise TimeoutError("unavailable")
    p = DynamicPlanner(Settings(llm_router_enabled=True), chat_model=UnavailableModel())
    plan = p.plan("查询订单123")
    assert plan.source == "rules"
    assert [t.capability for t in plan.tasks] == ["trade.query"]


@pytest.mark.parametrize("capability,intent", [
    ("trade.workflow", None),
    ("trade.workflow", TradeIntent(action="query")),
    ("trade.advise", None),
    ("trade.query", TradeIntent(action="create", package_id=42)),
])
def test_trade_capability_must_match_its_structured_action(capability, intent):
    plan = ExecutionPlan(run_id="run-12345678", goal="test", source="llm",
        confidence=1, reason="test", tasks=[AgentTask(id="trade", agent="trade",
            capability=capability, objective="test", trade_intent=intent)])
    with pytest.raises(PlanValidationError, match="trade intent"):
        validate_plan(plan)


def test_rule_fallback_does_not_create_a_negated_transaction():
    plan = DynamicPlanner(Settings(llm_router_enabled=False)).plan(
        "不要帮我下单，只查询套餐42库存"
    )
    assert all(t.capability != "trade.workflow" for t in plan.tasks)


def test_model_cannot_invent_seckill_activity_ids():
    candidate = task("buy", "trade", "trade.workflow", "秒杀购买活动999",
                     trade_intent={"action": "create", "trade_type": "SECKILL", "activity_id": 999})
    plan = planner(candidate).plan("帮我秒杀购买活动12，数量1")
    assert plan.source == "rules"


def test_explicit_group_team_alias_is_a_valid_id_source():
    candidate = task("buy", "trade", "trade.workflow", "参团套餐42，团队abc，数量1",
                     trade_intent={"action": "create", "trade_type": "GROUP_BUY", "package_id": 42,
                                   "team_id": "abc", "group_mode": "join", "quantity": 1})
    plan = planner(candidate).plan("参团套餐42，团队abc，数量1")
    assert plan.source == "llm"


def test_json_mode_prompt_carries_the_plan_schema():
    class JsonModeModel:
        def with_structured_output(self, schema, **kwargs):
            class Structured:
                def invoke(self, messages):
                    sent_schema = json.loads(messages[0][1].split("\nJSON Schema:\n", 1)[1])
                    assert sent_schema == PlanDraft.model_json_schema()
                    return {"tasks": [task("stock", "trade", "trade.query", "查询套餐42库存")]}
            return Structured()
    p = DynamicPlanner(Settings(llm_router_enabled=True, llm_provider="zhipu"), chat_model=JsonModeModel())
    plan = p.plan("查询套餐42库存")
    assert plan.source == "llm"


def test_english_pay_order_objective_is_not_a_payment_number():
    plan = planner(task("pay", "trade", "trade.workflow", "Pay order 123",
                        trade_intent={"action": "pay", "order_id": 123})).plan("帮我支付订单123")
    assert plan.source == "llm"


def test_fallback_object_noun_does_not_request_recommendations():
    plan = DynamicPlanner(Settings(llm_router_enabled=False)).plan("查询套餐42库存")
    assert [t.capability for t in plan.tasks] == ["trade.query"]


@pytest.mark.parametrize("message", [
    "不下单，只查询套餐42库存", "无需下单，只查询套餐42库存",
    "只查询套餐42库存，不下单", "帮我支付订单123，但是先别执行",
])
def test_fallback_abstains_from_other_negated_operations(message):
    plan = DynamicPlanner(Settings(llm_router_enabled=False)).plan(message)
    assert all(t.capability != "trade.workflow" for t in plan.tasks)


def test_negated_coupon_in_another_clause_does_not_negate_the_purchase():
    plan = DynamicPlanner(Settings(llm_router_enabled=False)).plan("不要优惠券，帮我下单套餐42数量1")
    assert any(t.capability == "trade.workflow" for t in plan.tasks)


@pytest.mark.parametrize("message,objective", [
    ("Explain group-buy rules", "Explain how teams work"),
    ("Explain how to order", "Explain the request process"),
])
def test_english_business_words_are_not_string_identifiers(message, objective):
    plan = planner(task("policy", "support", "support.run", objective)).plan(message)
    assert plan.source == "llm"


def test_package_stock_cannot_use_the_order_listing_intent():
    plan = ExecutionPlan(run_id="run-12345678", goal="查询套餐42库存", source="llm",
        confidence=1, reason="test", tasks=[AgentTask(id="stock", agent="trade",
            capability="trade.query", objective="查询套餐42库存",
            trade_intent=TradeIntent(action="query", package_id=42))])
    with pytest.raises(PlanValidationError, match="package facts"):
        validate_plan(plan)


def test_replan_can_use_activity_ids_from_successful_structured_facts():
    initial = ExecutionPlan(run_id="run-12345678", goal="查询可用秒杀活动后查看详情",
        source="rules", confidence=1, reason="test", tasks=[
            AgentTask(id="activities", agent="trade", capability="trade.query", objective="查询秒杀活动")])
    result = TaskResult(task_id="activities", agent="trade", status="succeeded",
        artifact=TaskArtifact(facts=[{"data": {"activities": [{"activityId": 7, "packageId": 42}]}}]))
    p = planner(task("detail", "trade", "trade.query", "查询秒杀活动7详情",
                     trade_intent={"action": "query", "query_kind": "seckill_activities", "activity_id": 7}))
    assert p.replan(initial, [result]) is not None


def test_diner_count_cannot_become_purchase_quantity():
    plan = planner(task("buy", "trade", "trade.workflow", "购买适合两人的套餐",
                        trade_intent={"action": "create", "quantity": 2})).plan(
        "推荐适合两人的套餐，合适就帮我买"
    )
    assert plan.source == "rules"


def test_english_payment_words_are_not_payment_identifiers():
    plan = planner(task("policy", "support", "support.run", "Explain payment notification rules")).plan(
        "Explain payment rules"
    )
    assert plan.source == "llm"


def test_price_cannot_become_purchase_quantity_in_llm_or_fallback():
    plan = planner(task("buy", "trade", "trade.workflow", "购买套餐12",
                        trade_intent={"action": "create", "package_id": 12, "quantity": 42})).plan(
        "帮我买42元的套餐12"
    )
    assert plan.source == "rules"
    assert all(t.trade_intent is None or t.trade_intent.quantity is None for t in plan.tasks)


def test_invalid_history_quantity_does_not_hide_current_valid_quantity():
    message = "帮我普通下单套餐42，数量2"
    plan = planner(task("buy", "trade", "trade.workflow", message,
        trade_intent={"action": "create", "package_id": 42, "quantity": 2})).plan(
            message, history=[SessionTurn(user="数量100", assistant="请重新输入数量", intent="trade")])
    assert plan.source == "llm" and plan.tasks[0].trade_intent.quantity == 2


@pytest.mark.parametrize("message", ["帮我下单套餐42，数量2", "帮我下单套餐42，-2份"])
def test_current_quantity_cannot_be_replaced_by_old_quantity(message):
    plan = planner(task("buy", "trade", "trade.workflow", message,
        trade_intent={"action": "create", "package_id": 42, "quantity": 1})).plan(
            message, history=[SessionTurn(user="数量1", assistant="好的", intent="trade")])
    assert plan.source == "rules"
    assert next(t for t in plan.tasks if t.capability == "trade.workflow").trade_intent.quantity != 1


def test_package_query_draft_is_adapted_to_the_existing_read_chain():
    plan = planner(task("stock", "trade", "trade.query", "查询实时库存",
                        trade_intent={"action": "query", "package_id": 42})).plan("查询套餐42库存")
    assert plan.source == "llm"
    assert plan.tasks[0].trade_intent is None
    assert "42" in plan.tasks[0].objective


def test_candidate_stock_query_requires_the_discovery_dependency():
    plan = ExecutionPlan(run_id="run-12345678", goal="推荐并查询库存", source="llm",
        confidence=1, reason="test", tasks=[
            AgentTask(id="recommend", agent="recommend", capability="recommend.run", objective="推荐套餐"),
            AgentTask(id="stock", agent="trade", capability="trade.query", objective="查询候选套餐库存"),
        ])
    with pytest.raises(PlanValidationError, match="candidate"):
        validate_plan(plan)


@pytest.mark.parametrize("repair_succeeds", [True, False])
def test_invalid_plan_gets_at_most_one_feedback_repair(repair_succeeds):
    class RepairModel:
        calls = 0
        def with_structured_output(self, schema, **kwargs):
            owner = self
            class Structured:
                def invoke(self, messages):
                    owner.calls += 1
                    capability = "trade.query" if repair_succeeds and owner.calls == 2 else "trade.invalid"
                    return {"tasks": [task("query", "trade", capability, "查询订单123")]}
            return Structured()
    model = RepairModel()
    plan = DynamicPlanner(Settings(llm_router_enabled=True), chat_model=model).plan("查询订单123")
    assert model.calls == 2
    assert plan.source == ("llm" if repair_succeeds else "rules")
    assert [t.capability for t in plan.tasks] == ["trade.query"]


@pytest.mark.parametrize("text,quantity", [("十二份", 12), ("二十份", 20), ("二十三份", 23)])
def test_chinese_quantity_is_not_truncated_to_its_suffix(text, quantity):
    message = "帮我普通下单套餐42，" + text
    plan = planner(task("buy", "trade", "trade.workflow", message,
                        trade_intent={"action": "create", "package_id": 42, "quantity": quantity})).plan(message)
    assert plan.source == "llm"
    assert plan.tasks[0].trade_intent.quantity == quantity
    fallback = DynamicPlanner(Settings(llm_router_enabled=False)).plan(message)
    assert next(t for t in fallback.tasks if t.capability == "trade.workflow").trade_intent.quantity == quantity


def test_recommendation_does_not_block_an_independent_explicit_order_query():
    plan = ExecutionPlan(run_id="run-12345678", goal="Recommend a restaurant and check order 123 price",
        source="llm", confidence=1, reason="test", tasks=[
            AgentTask(id="recommend", agent="recommend", capability="recommend.run", objective="Recommend a restaurant"),
            AgentTask(id="order", agent="trade", capability="trade.query", objective="Check order 123 price"),
        ])
    assert validate_plan(plan).tasks[1].depends_on == []


@pytest.mark.parametrize("text,wrong_quantity", [("1.5份", 5), ("-2份", 2), ("负两份", 2)])
def test_invalid_quantity_tokens_cannot_be_truncated(text, wrong_quantity):
    plan = planner(task("buy", "trade", "trade.workflow", "购买套餐42",
                        trade_intent={"action": "create", "package_id": 42, "quantity": wrong_quantity})).plan(
        "帮我普通下单套餐42，" + text
    )
    assert plan.source == "rules"
    assert all(t.trade_intent is None or t.trade_intent.quantity is None for t in plan.tasks)

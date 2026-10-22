import pytest

from app.core.config import Settings
from app.tools.models import ToolCall, ToolContext
from app.tools.executor import ToolExecutor
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.actions import ActionResult, TradeActionClient, build_action_registry, build_action_call
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient
from app.trade.intent import parse_intent, missing_fields
from app.trade.service import TradeWorkflowService


def test_group_preview_rejects_a_different_explicit_activity():
    from app.trade.intent import TradeIntent
    from app.trade.purchase import PurchaseFactsError, PurchaseReader

    reader = PurchaseReader(Reads(), Settings(llm_router_enabled=False))
    with pytest.raises(PurchaseFactsError, match="活动"):
        reader.preview(TradeIntent(action="create", trade_type="GROUP_BUY", group_mode="open",
                                   package_id=1, quantity=1, activity_id=999), "auth")


class Reads:
    group_price = 12000
    can_join = True
    request_status = "INIT"
    request_available = True
    def result(self, data):
        return GatewayQueryResult(capability="test", status="success", data=data)
    def get_package_snapshot(self, package_id):
        return self.result({"packageId": package_id, "price": 16800, "stock": 97})
    def get_group_buy_activity(self, package_id, authorization):
        return self.result({"activityId": 3, "packageId": package_id, "groupPrice": self.group_price, "canBuy": True, "targetCount": 3})
    def get_group_buy_team(self, team_id, authorization):
        return self.result({"teamId": team_id, "activityId": 3, "packageId": 1, "remainingCount": 1, "canJoin": self.can_join})
    def list_group_buy_teams(self, package_id, authorization, limit=10):
        return self.result({"teams": [{"teamId": "GBT1", "remainingCount": 1}]})
    def get_seckill_activity(self, activity_id, authorization):
        return self.result({"activityId": activity_id, "packageId": 1, "seckillPrice": 9900, "canBuy": True, "userTakeLimit": 1})
    def list_seckill_activities(self, authorization, package_id=None, limit=20):
        return self.result({"activities": [{"activityId": 7, "packageId": 1, "seckillPrice": 9900, "canBuy": True}]})
    def get_seckill_request(self, request_no, authorization):
        if not self.request_available:
            return GatewayQueryResult(capability="request", status="unavailable")
        return self.result({"requestNo": request_no, "requestStatus": self.request_status,
            "orderId": 42 if self.request_status == "SUCCESS" else None, "failReason": "库存不足"})
    def list_my_coupons(self, authorization, coupon_status="UNUSED", limit=10):
        return self.result({"coupons": []})
    def get_payment(self, pay_order_no, authorization):
        return self.result({"payOrderNo": pay_order_no, "payStatus": "PREPARED", "payAmount": 9900})


class Actions:
    def __init__(self): self.calls = []
    def create(self, args, context):
        self.calls.append(("NORMAL", args, context))
        return ActionResult(status="success", data={"orderId": 40})
    def group_buy(self, args, context):
        self.calls.append(("GROUP_BUY", args, context))
        return ActionResult(status="success", data={"orderId": 41, "teamId": args.team_id or "NEW_TEAM"})
    def seckill(self, args, context):
        self.calls.append(("SECKILL", args, context))
        return ActionResult(status="success", data={"requestNo": "SKR123", "requestStatus": "INIT"})
    def sync_payment(self, args, context):
        self.calls.append(("SYNC", args, context))
        return ActionResult(status="success", data={"payOrderNo": args.pay_order_no, "payStatus": "SUCCESS"})
    def pay(self, args, context): return ActionResult(status="success", data={})
    def cancel(self, args, context): return ActionResult(status="success", data={})
    def refund(self, args, context): return ActionResult(status="success", data={})


@pytest.fixture
def modes(tmp_path):
    reads, actions = Reads(), Actions()
    service = TradeWorkflowService(Settings(trade_checkpoint_path=str(tmp_path / "trade.sqlite")), read_client=reads, action_client=actions)
    yield service, reads, actions
    service.close()


def turn(service, message="", prior=None, approved=None, recover=False):
    return service.turn(user_id="7", authorization="test-token", session_id="modes", message=message,
        thread_id=prior["thread_id"] if prior else None, approved=approved,
        confirmation_id=prior["interrupt"]["confirmation_id"] if approved is not None else None, recover=recover)


@pytest.mark.parametrize("message,mode", [("普通下单套餐1，数量2", "NORMAL"), ("开团套餐1，数量1", "GROUP_BUY"), ("参加秒杀活动7，数量1", "SECKILL")])
def test_purchase_type_never_falls_back_to_normal(modes, message, mode):
    service, _, actions = modes
    preview = turn(service, message)
    assert preview["status"] == "awaiting_confirmation"
    assert preview["trade_type"] == mode
    assert not actions.calls
    done = turn(service, prior=preview, approved=True)
    assert actions.calls[0][0] == mode
    assert done["status"] == ("awaiting_result" if mode == "SECKILL" else "completed")
    if mode != "SECKILL":
        assert turn(service, prior=preview, approved=True)["status"] == done["status"]
    assert len(actions.calls) == 1


def test_group_mode_and_team_are_collected_not_invented(modes):
    service, _, actions = modes
    draft = turn(service, "拼团下单套餐1，数量1")
    assert draft["interrupt"]["missing_fields"] == ["group_mode"]
    draft = turn(service, "参团", prior=draft)
    assert draft["interrupt"]["missing_fields"] == ["team_id"]
    preview = turn(service, "团队GBT1", prior=draft)
    assert preview["interrupt"]["preview"]["team_id"] == "GBT1"
    turn(service, prior=preview, approved=True)
    assert actions.calls[0][1].team_id == "GBT1"


@pytest.mark.parametrize("message", ["开团套餐1，数量2", "参加秒杀活动7，数量2", "开团套餐1，数量1，优惠券3"])
def test_mode_constraints_block_all_writes(modes, message):
    service, _, actions = modes
    assert turn(service, message)["status"] == "failed"
    assert not actions.calls


def test_group_price_or_team_changes_require_new_confirmation(modes):
    service, reads, actions = modes
    preview = turn(service, "参团套餐1，数量1，团队GBT1")
    reads.group_price += 1
    assert turn(service, prior=preview, approved=True)["status"] == "stale"
    assert not actions.calls
    preview = turn(service, "参团套餐1，数量1，团队GBT1")
    reads.can_join = False
    assert turn(service, prior=preview, approved=True)["status"] == "failed"
    assert not actions.calls


def test_seckill_wait_survives_restart_and_never_resubmits(modes):
    service, reads, actions = modes
    pending = turn(service, prior=turn(service, "参加秒杀活动7，数量1"), approved=True)
    assert pending["interrupt"]["type"] == "seckill_wait"
    restarted = TradeWorkflowService(service.settings, read_client=reads, action_client=actions)
    try:
        reads.request_available = False
        assert turn(restarted, prior=pending, recover=True)["status"] == "awaiting_result"
        reads.request_available = True
        reads.request_status = "SUCCESS"
        done = turn(restarted, prior=pending, recover=True)
        assert done["status"] == "completed" and done["result"]["data"]["orderId"] == 42
        assert len(actions.calls) == 1
    finally: restarted.close()


def test_advice_uses_real_options_and_preserves_user_constraints(modes):
    service, _, actions = modes
    done = turn(service, "套餐1怎么买划算，预算150元，不想等成团")
    data = done["result"]["data"]
    assert data["recommended"]["trade_type"] == "SECKILL"
    assert data["recommended"]["price_cents"] == 9900
    assert not next(x for x in data["options"] if x["trade_type"] == "GROUP_BUY")["matches_preferences"]
    assert not actions.calls
    preview = turn(service, data["recommended"]["message"])
    assert preview["status"] == "awaiting_confirmation" and preview["trade_type"] == "SECKILL"


def test_payment_reconciliation_is_a_confirmed_tool(modes):
    service, _, actions = modes
    preview = turn(service, "同步支付单PAY123")
    assert preview["status"] == "awaiting_confirmation"
    assert not actions.calls
    assert turn(service, prior=preview, approved=True)["result"]["data"]["payStatus"] == "SUCCESS"


@pytest.mark.parametrize("phrase", ["核对付款", "核对支付"])
def test_payment_sync_alias_is_one_action(phrase):
    intent = parse_intent(phrase + "，支付单PAY123", {}, Settings())
    assert intent.action == "sync_payment" and intent.pay_order_no == "PAY123"


@pytest.mark.parametrize("previous,message", [
    ({}, "申请退款订单42，退款原因：付款后不想要了，取消订单"),
    ({"action": "refund", "order_id": 42}, "退款原因：付款后不想要了，取消订单")])
def test_refund_reason_does_not_switch_action(previous, message):
    intent = parse_intent(message, previous, Settings())
    assert intent.action == "refund" and intent.order_id == 42
    assert intent.refund_reason == "付款后不想要了，取消订单"
    assert not missing_fields(intent)


def test_multiple_real_mutations_are_still_rejected():
    assert parse_intent("核对付款支付单PAY123，然后取消订单42", {}, Settings()).action == "unknown"


def test_advice_does_not_recommend_off_shelf_package(modes):
    service, reads, actions = modes
    reads.get_package_snapshot = lambda package_id: reads.result({"packageId": package_id, "price": 16800, "stock": 97, "packageStatus": 0})
    data = turn(service, "套餐1怎么买划算")["result"]["data"]
    assert data["options"] == [] and data["recommended"] is None
    assert not actions.calls


def test_every_added_tool_has_schema_and_writes_require_exact_approval():
    reads = build_trade_tool_registry(TradeGatewayClient(Settings()))
    writes = build_action_registry(TradeActionClient(Settings()))
    assert len(reads.llm_schemas()) == 14
    assert len(writes.llm_schemas()) == 7
    for schema in writes.llm_schemas():
        name = schema["function"]["name"]
        assert writes.get(name).risk == "write"
        assert ToolExecutor(writes).execute([ToolCall(name=name)], ToolContext(authorization="token", allowed_tool_names=frozenset({name})))[0].status == "rejected"
    for schema in reads.llm_schemas():
        assert reads.get(schema["function"]["name"]).risk == "read"


def test_mode_override_and_ambiguous_modes():
    previous = parse_intent("拼团下单套餐1，数量1", {}, Settings()).model_dump()
    normal = parse_intent("普通", previous, Settings())
    assert normal.trade_type == "NORMAL"
    mixed = parse_intent("普通拼团都要下单套餐1，数量1", {}, Settings())
    assert missing_fields(mixed) == ["trade_type"]
    query = parse_intent("查询秒杀活动", {}, Settings())
    assert query.action == "query" and query.query_kind == "seckill_activities"


def test_model_cannot_default_to_opening_a_group():
    class Model:
        def with_structured_output(self, schema, **kwargs): return self
        def invoke(self, messages):
            return {"action": "create", "trade_type": "GROUP_BUY", "package_id": 1, "quantity": 1, "group_mode": "open"}
    intent = parse_intent("拼团下单套餐1，数量1", {}, Settings(llm_router_enabled=True), Model())
    assert missing_fields(intent) == ["group_mode"]


def test_write_proposal_does_not_call_model_or_switch_purchase_tool():
    registry = build_action_registry(TradeActionClient(Settings()))
    call = build_action_call("create", {"trade_type": "GROUP_BUY", "package_id": 1, "quantity": 1,
        "team_id": "GBT1", "expected_unit_price": 12000, "expected_activity_id": 3}, registry)
    assert call.name == "trade_create_group_buy" and call.arguments["team_id"] == "GBT1"


def test_new_gateway_read_paths_forward_only_authenticated_user(monkeypatch):
    import httpx
    paths = []
    def get(url, **kwargs):
        paths.append((url, kwargs))
        return httpx.Response(200, json={"code": "0000", "data": {}}, request=httpx.Request("GET", url))
    monkeypatch.setattr("app.trade.gateway_client.httpx.get", get)
    client = TradeGatewayClient(Settings(java_gateway_base_url="http://gateway"))
    registry = build_trade_tool_registry(client)
    for name, args, suffix in [
        ("trade_get_group_buy_activity", {"package_id": 1}, "/api/trade/group-buy/activities/package/1"),
        ("trade_list_seckill_activities", {"package_id": 1}, "/api/trade/seckill/activities"),
        ("trade_get_seckill_activity", {"activity_id": 7}, "/api/trade/seckill/activities/7"),
        ("trade_get_seckill_request", {"request_no": "SKR1"}, "/api/trade/seckill/order-requests/SKR1"),
        ("trade_get_payment", {"pay_order_no": "PAY1"}, "/api/trade/pay/orders/PAY1")]:
        result = ToolExecutor(registry).execute([ToolCall(name=name, arguments=args)], ToolContext(authorization="auth", allowed_tool_names=frozenset({name})))[0]
        assert result.succeeded and result.result.has_data
        assert paths[-1][0] == "http://gateway" + suffix
        assert paths[-1][1]["headers"]["authorization"] == "auth"
    count = len(paths)
    result = ToolExecutor(registry).execute([ToolCall(name="trade_get_seckill_request", arguments={"request_no": "SKR1"})], ToolContext(allowed_tool_names=frozenset({"trade_get_seckill_request"})))[0]
    assert result.status == "missing_authorization" and len(paths) == count


def test_creation_adapters_send_stable_keys_and_do_not_accept_missing_results(monkeypatch):
    import httpx
    requests = []
    responses = [{"code": "0000", "data": {"orderId": 41}}, {"code": "0000", "data": {"requestNo": "SKR1"}}, {"code": "0000", "data": {}}]
    def post(url, **kwargs):
        requests.append((url, kwargs))
        return httpx.Response(200, json=responses.pop(0), request=httpx.Request("POST", url))
    monkeypatch.setattr("app.trade.actions.httpx.post", post)
    client = TradeActionClient(Settings(java_gateway_base_url="http://gateway"))
    registry = build_action_registry(client)
    for mode, args, path in [("GROUP_BUY", {"package_id": 1, "quantity": 1, "expected_unit_price": 12000, "expected_activity_id": 3}, "/api/trade/orders/group-buy"),
                            ("SECKILL", {"activity_id": 7, "quantity": 1, "expected_unit_price": 9900}, "/api/trade/orders/seckill/async")]:
        call = build_action_call("create", {**args, "trade_type": mode}, registry)
        result = ToolExecutor(registry).execute([call], ToolContext(authorization="auth", operation_id="same-operation", approved_call=call, allowed_tool_names=frozenset({call.name})))[0]
        assert result.result.status == "success"
        assert requests[-1][0] == "http://gateway" + path
        assert requests[-1][1]["json"]["requestId"] == "same-operation"
        assert requests[-1][1]["json"]["expectedUnitPrice"] == args["expected_unit_price"]
    call = build_action_call("create", {"trade_type": "SECKILL", "activity_id": 7, "quantity": 1, "expected_unit_price": 9900}, registry)
    result = ToolExecutor(registry).execute([call], ToolContext(authorization="auth", operation_id="same-operation", approved_call=call, allowed_tool_names=frozenset({call.name})))[0]
    assert result.result.status == "uncertain"

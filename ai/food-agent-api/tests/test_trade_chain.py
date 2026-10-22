from typing import Any
from types import SimpleNamespace

from app.agents.trade_agent import trade_agent_node
from app.core.config import get_settings
from app.rag.chain_models import AgentAnswer
from app.trade.chain import (
    TradeChain,
    TradeChainResult,
    TradeQueryRewrite,
)
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient


def _success(
    capability: str,
    reference_id: str,
    title: str,
    data: object,
) -> GatewayQueryResult:
    return GatewayQueryResult(
        capability=capability,
        status="success",
        endpoint="/test",
        data=data,
        reference_id=reference_id,
        reference_title=title,
    )


class FakeGatewayClient:
    def __init__(self) -> None:
        self.calls: list[tuple[object, ...]] = []

    def get_package_snapshot(self, package_id: int) -> GatewayQueryResult:
        self.calls.append(("package", package_id))
        return _success(
            "package_snapshot",
            f"package:{package_id}:trade-snapshot",
            "套餐交易快照",
            {"packageId": package_id, "price": 6800, "stock": 12},
        )

    def list_coupon_templates(self, authorization: str, limit: int = 10) -> GatewayQueryResult:
        self.calls.append(("coupon_templates", authorization, limit))
        return _success("coupon_query", "coupon:templates", "可领取优惠券", {"templates": []})

    def list_my_coupons(
        self,
        authorization: str,
        coupon_status: str = "UNUSED",
        limit: int = 10,
    ) -> GatewayQueryResult:
        self.calls.append(("my_coupons", authorization, coupon_status, limit))
        return _success(
            "coupon_query",
            "coupon:mine",
            "当前用户优惠券",
            {"coupons": [{"userCouponId": 9, "discountAmount": 1000}]},
        )

    def list_group_buy_teams(
        self,
        package_id: int,
        authorization: str,
        limit: int = 10,
    ) -> GatewayQueryResult:
        self.calls.append(("group_teams", package_id, authorization, limit))
        return _success("group_buy_query", "group:teams", "可参与拼团", {"teams": []})

    def get_group_buy_team(self, team_id: str, authorization: str) -> GatewayQueryResult:
        self.calls.append(("group_team", team_id, authorization))
        return _success("group_buy_query", f"group:{team_id}", "拼团队伍", {"teamId": team_id})

    def list_my_group_buy_orders(
        self,
        authorization: str,
        page_size: int = 10,
    ) -> GatewayQueryResult:
        self.calls.append(("my_group_orders", authorization, page_size))
        return _success("group_buy_query", "group:mine", "我的拼团", {"orders": []})

    def get_order(self, order_id: int, authorization: str) -> GatewayQueryResult:
        self.calls.append(("order", order_id, authorization))
        return _success(
            "order_query",
            f"order:{order_id}",
            "订单实时详情",
            {"orderId": order_id, "orderStatus": "PAID"},
        )

    def list_orders(self, authorization: str, page_size: int = 10) -> GatewayQueryResult:
        self.calls.append(("orders", authorization, page_size))
        return _success("order_query", "order:mine", "当前用户订单", {"orders": []})


def _chain(gateway_client: FakeGatewayClient, **settings: object) -> TradeChain:
    return TradeChain(
        settings=get_settings().model_copy(
            update={"llm_router_enabled": False, **settings}
        ),
        gateway_client=gateway_client,  # type: ignore[arg-type]
    )


def test_sales_query_reads_business_snapshot() -> None:
    gateway = FakeGatewayClient()
    result = _chain(gateway).run("查一下套餐12的销量")
    assert "sales_query" in result.capabilities
    assert gateway.calls == [("package", 12)]


def test_trade_price_and_stock_share_one_package_snapshot_query() -> None:
    gateway = FakeGatewayClient()

    result = _chain(gateway).run("查询套餐 12 的价格和库存")

    assert gateway.calls == [("package", 12)]
    assert result.capabilities == ["price_query", "stock_query"]
    assert result.operation_executed is False
    assert result.references[0]["id"] == "package:12:trade-snapshot"
    assert "price" in result.response
    assert "stock" in result.response


def test_trade_uses_package_id_from_upstream_artifact() -> None:
    gateway = FakeGatewayClient()

    result = _chain(gateway).run(
        "\u67e5\u8be2\u5b9e\u65f6\u5e93\u5b58",
        upstream_artifacts={
            "recommend": {
                "package_ids": [42],
                "shop_ids": [],
                "order_ids": [],
                "facts": [],
                "references": [],
            }
        },
    )

    assert gateway.calls == [("package", 42)]
    assert result.capabilities == ["stock_query"]


def test_trade_order_query_forwards_authorization() -> None:
    gateway = FakeGatewayClient()

    result = _chain(gateway).run("查询订单 12345 的详情", authorization="token-1")

    assert gateway.calls == [("order", 12345, "token-1")]
    assert result.facts[0]["data"] == {"orderId": 12345, "orderStatus": "PAID"}
    assert result.references[0]["source"] == "java_gateway_api"


def test_trade_order_preview_combines_snapshot_and_user_coupons_without_writes() -> None:
    gateway = FakeGatewayClient()

    result = _chain(gateway).run(
        "预览套餐 12 的订单，数量 2，看看可用优惠券",
        authorization="token-1",
    )

    assert gateway.calls == [
        ("package", 12),
        ("my_coupons", "token-1", "UNUSED", 10),
    ]
    preview = next(fact for fact in result.facts if fact["id"] == "order-preview:package:12")
    assert preview["data"]["quantity"] == 2  # type: ignore[index]
    assert preview["data"]["estimatedTotalAmountBeforeDiscount"] == 13600  # type: ignore[index]
    assert preview["data"]["amountUnit"] == "cents"  # type: ignore[index]
    assert preview["data"]["previewOnly"] is True  # type: ignore[index]
    assert result.operation_executed is False


def test_trade_mutation_request_is_blocked_before_gateway_query() -> None:
    gateway = FakeGatewayClient()

    result = _chain(gateway).run("帮我取消订单 12345", authorization="token-1")

    assert gateway.calls == []
    assert "不能执行取消订单" in result.response
    assert result.query_statuses == [
        {"status": "blocked_operation", "operation": "取消订单"}
    ]
    assert result.operation_executed is False


def test_trade_agent_node_uses_chain_and_forwards_authorization(monkeypatch) -> None:
    expected = TradeChainResult(
        response="订单当前为已支付。",
        rag_mode="trade_agent_java_query",
        query="查询订单 12345",
        rewritten_query="查询订单 12345",
        capabilities=["order_query"],
    )

    class FakeTradeChain:
        def run(
            self,
            message: str,
            authorization: str = "",
            upstream_artifacts: dict[str, object] | None = None,
            session_history: list[object] | None = None,
        ) -> TradeChainResult:
            assert message == "查询订单 12345"
            assert authorization == "token-1"
            assert upstream_artifacts == {}
            assert session_history == []
            return expected

    monkeypatch.setattr("app.agents.trade_agent.TradeChain", lambda: FakeTradeChain())

    result = trade_agent_node(
        {"message": "查询订单 12345", "authorization": "token-1"}
    )

    assert result == expected.model_dump()


def test_trade_gateway_client_uses_gateway_get_and_authorization(monkeypatch) -> None:
    class FakeResponse:
        status_code = 200

        @staticmethod
        def json() -> dict[str, object]:
            return {
                "code": "0000",
                "data": {"orderId": 12345, "orderStatus": "PAID"},
            }

    def fake_get(
        url: str,
        headers: dict[str, str],
        params: dict[str, object] | None,
        timeout: float,
        trust_env: bool,
    ) -> FakeResponse:
        assert url == "http://127.0.0.1:8080/api/trade/orders/12345"
        assert headers == {"authorization": "token-1"}
        assert params is None
        assert timeout == 5.0
        assert trust_env is False
        return FakeResponse()

    monkeypatch.setattr("app.trade.gateway_client.httpx.get", fake_get)

    result = TradeGatewayClient(settings=get_settings()).get_order(12345, "token-1")

    assert result.status == "success"
    assert result.data == {"orderId": 12345, "orderStatus": "PAID"}


def test_trade_chain_uses_structured_rewrite_and_answer() -> None:
    gateway = FakeGatewayClient()
    rewrite = TradeQueryRewrite(
        search_query="套餐 12 实时价格和库存",
        capabilities=["price_query"],
        reason="The user asks for current package facts.",
    )
    answer = AgentAnswer(
        answer="套餐 12 当前价格为 6800，库存为 12。",
        used_reference_ids=["package:12:trade-snapshot"],
    )

    class StructuredModel:
        def __init__(self, result: object) -> None:
            self.result = result

        def invoke(self, messages: list[tuple[str, str]]) -> object:
            assert messages
            return self.result

    class ChatModel:
        def with_structured_output(self, schema: object, **kwargs: Any) -> StructuredModel:
            assert kwargs == {"method": "json_mode"}
            if schema is TradeQueryRewrite:
                return StructuredModel(rewrite)
            if schema is AgentAnswer:
                return StructuredModel(answer)
            raise AssertionError("unexpected schema")

    result = TradeChain(
        settings=get_settings().model_copy(
            update={
                "llm_router_enabled": True,
                "llm_provider": "zhipu",
                "llm_api_key": "test-key",
            }
        ),
        gateway_client=gateway,  # type: ignore[arg-type]
        chat_model=ChatModel(),
    ).run("查询套餐 12 的价格和库存")

    assert gateway.calls == [("package", 12)]
    assert result.rewritten_query == rewrite.search_query
    assert result.capabilities == ["price_query", "stock_query"]
    assert result.response == answer.answer


def test_open_read_model_can_choose_an_alternative_tool_and_limit():
    gateway = FakeGatewayClient()

    class Model:
        bound_names = set()

        def with_structured_output(self, schema, **kwargs):
            if schema is TradeQueryRewrite:
                return SimpleNamespace(invoke=lambda _: TradeQueryRewrite(
                    search_query="查看优惠券", capabilities=["coupon_query"], reason="coupon lookup"))
            return SimpleNamespace(invoke=lambda _: AgentAnswer(
                answer="当前有一张优惠券。", used_reference_ids=["coupon:mine"]))

        def bind_tools(self, schemas):
            self.bound_names = {schema["function"]["name"] for schema in schemas}
            return SimpleNamespace(invoke=lambda _: SimpleNamespace(tool_calls=[{
                "name": "trade_list_my_coupons", "args": {"limit": 2}, "id": "coupon-call",
            }]))

    model = Model()
    result = TradeChain(settings=get_settings().model_copy(update={"llm_router_enabled": True}),
                        gateway_client=gateway, chat_model=model).run("查看优惠券", authorization="token")
    assert model.bound_names == {"trade_list_my_coupons", "trade_list_coupon_templates"}
    # The deterministic fallback is coupon templates, but the model can choose
    # the other eligible read; no forced fallback call is appended afterward.
    assert gateway.calls == [("my_coupons", "token", "UNUSED", 2)]
    assert result.facts[0]["data"]["coupons"][0]["userCouponId"] == 9


def test_explicit_order_scope_never_binds_or_executes_other_order_reads():
    gateway = FakeGatewayClient()

    class Model:
        binds = 0

        def with_structured_output(self, schema, **kwargs):
            if schema is TradeQueryRewrite:
                return SimpleNamespace(invoke=lambda _: TradeQueryRewrite(
                    search_query="查询订单42", capabilities=["order_query"], reason="exact lookup"))
            return SimpleNamespace(invoke=lambda _: AgentAnswer(
                answer="订单42已支付。", used_reference_ids=["order:42"]))

        def bind_tools(self, schemas):
            self.binds += 1
            raise AssertionError("exact ID lookup is deterministic")

    model = Model()
    TradeChain(settings=get_settings().model_copy(update={"llm_router_enabled": True}),
               gateway_client=gateway, chat_model=model).run("查询订单42", authorization="token")
    assert model.binds == 0
    assert gateway.calls == [("order", 42, "token")]

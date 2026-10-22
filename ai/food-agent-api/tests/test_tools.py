from types import SimpleNamespace

import pytest

from app.core.config import Settings
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult
from app.trade.tool_selector import build_read_scope, select_trade_tool_calls


class FakeGateway:
    def __init__(self) -> None:
        self.calls: list[tuple[object, ...]] = []

    def get_package_snapshot(self, package_id: int) -> GatewayQueryResult:
        self.calls.append(("package", package_id))
        return GatewayQueryResult(
            capability="package_snapshot",
            status="success",
            data={"packageId": package_id},
        )

    def list_orders(self, authorization: str, page_size: int = 10) -> GatewayQueryResult:
        self.calls.append(("orders", authorization, page_size))
        return GatewayQueryResult(
            capability="order_query",
            status="success",
            data={"orders": []},
        )


def test_tool_executor_validates_arguments_before_gateway_call() -> None:
    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)  # type: ignore[arg-type]

    executions = ToolExecutor(registry).execute(
        [
            ToolCall(
                name="trade_get_package_snapshot",
                arguments={"package_id": -1},
            )
        ],
        ToolContext(allowed_tool_names=frozenset({"trade_get_package_snapshot"})),
    )

    assert executions[0].status == "invalid_arguments"
    assert gateway.calls == []


def test_tool_executor_enforces_authorization_before_gateway_call() -> None:
    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)  # type: ignore[arg-type]

    executions = ToolExecutor(registry).execute(
        [ToolCall(name="trade_list_my_orders")],
        ToolContext(allowed_tool_names=frozenset({"trade_list_my_orders"})),
    )

    assert executions[0].status == "missing_authorization"
    assert gateway.calls == []


def test_llm_conflicting_id_and_write_call_fall_back_to_known_read() -> None:
    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)  # type: ignore[arg-type]
    required = [
        ToolCall(
            name="trade_get_package_snapshot",
            arguments={"package_id": 12},
        )
    ]

    class BoundModel:
        def invoke(self, messages: list[tuple[str, str]]) -> object:
            assert messages
            return SimpleNamespace(
                tool_calls=[
                    {
                        "id": "call-1",
                        "name": "trade_get_package_snapshot",
                        "args": {"package_id": 999},
                    },
                    {
                        "id": "call-2",
                        "name": "trade_create_order",
                        "args": {"package_id": 12},
                    },
                ]
            )

    class ChatModel:
        bound = False

        def bind_tools(self, schemas: list[dict[str, object]]) -> BoundModel:
            self.bound = True
            names = {
                str(schema["function"]["name"])  # type: ignore[index]
                for schema in schemas
            }
            assert names == {"trade_get_package_snapshot", "trade_list_my_orders"}
            return BoundModel()

    model = ChatModel()
    calls = select_trade_tool_calls(
        settings=Settings(
            llm_router_enabled=True,
            llm_api_key="test-key",
        ),
        registry=registry,
        message="package 12 price",
        capabilities={"price_query", "order_query"},
        fallback_calls=required,
        context=ToolContext(authorization="token",
            allowed_tool_names=frozenset({"trade_get_package_snapshot", "trade_list_my_orders"}),
            argument_constraints={"trade_get_package_snapshot": {"package_id": 12}}),
        chat_model=model,
    )
    assert model.bound
    assert calls == required
    ToolExecutor(registry).execute(calls, ToolContext(
        allowed_tool_names=frozenset({"trade_get_package_snapshot"}),
        argument_constraints={"trade_get_package_snapshot": {"package_id": 12}}))
    assert gateway.calls == [("package", 12)]


@pytest.mark.parametrize("allowed", [frozenset(), frozenset({"trade_list_my_orders"})])
def test_executor_rejects_out_of_scope_call_even_when_registered(allowed):
    gateway = FakeGateway()
    executor = ToolExecutor(build_trade_tool_registry(gateway))
    result = executor.execute([ToolCall(name="trade_get_package_snapshot", arguments={"package_id": 12})],
                              ToolContext(allowed_tool_names=allowed))[0]
    assert result.status == "rejected"
    assert not gateway.calls


def test_executor_locks_known_ids_independent_of_model_selector():
    gateway = FakeGateway()
    executor = ToolExecutor(build_trade_tool_registry(gateway))
    result = executor.execute([ToolCall(name="trade_get_package_snapshot", arguments={"package_id": 999})],
        ToolContext(allowed_tool_names=frozenset({"trade_get_package_snapshot"}),
            argument_constraints={"trade_get_package_snapshot": {"package_id": 12}}))[0]
    assert result.status == "rejected"
    assert not gateway.calls


def test_open_selection_can_choose_subset_and_optional_parameters():
    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)
    context = build_read_scope(registry, {"price_query", "order_query"}, {"package_id": 12}, "token")
    fallback = [ToolCall(name="trade_get_package_snapshot", arguments={"package_id": 12}),
                ToolCall(name="trade_list_my_orders")]

    class Model:
        bound_names = set()

        def bind_tools(self, schemas):
            self.bound_names = {schema["function"]["name"] for schema in schemas}
            return self

        def invoke(self, messages):
            return SimpleNamespace(tool_calls=[{"id": "chosen", "name": "trade_list_my_orders", "args": {"page_size": 3}}])

    model = Model()
    calls = select_trade_tool_calls(settings=Settings(llm_router_enabled=True), registry=registry,
        message="show three orders", capabilities={"price_query", "order_query"},
        fallback_calls=fallback, context=context, chat_model=model)
    assert model.bound_names == {"trade_get_package_snapshot", "trade_list_my_orders"}
    assert calls == [ToolCall(id="chosen", name="trade_list_my_orders", arguments={"page_size": 3})]
    ToolExecutor(registry).execute(calls, context)
    assert gateway.calls == [("orders", "token", 3)]


def test_known_singleton_skips_model_and_fixed_reads_skip_model():
    from app.trade.purchase import PurchaseReader

    class Model:
        binds = 0

        def bind_tools(self, schemas):
            self.binds += 1
            raise AssertionError("fixed calls must not bind a model")

    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)
    model = Model()
    context = build_read_scope(registry, {"price_query"}, {"package_id": 12}, "")
    fallback = [ToolCall(name="trade_get_package_snapshot", arguments={"package_id": 12})]
    calls = select_trade_tool_calls(settings=Settings(llm_router_enabled=True), registry=registry,
        message="package price", capabilities={"price_query"}, fallback_calls=fallback,
        context=context, chat_model=model)
    assert calls == fallback
    assert model.binds == 0
    # Here no exception boundary could disguise a bind_tools call.
    reader = PurchaseReader(gateway, Settings(llm_router_enabled=True))
    reader.read_many([("trade_get_package_snapshot", {"package_id": 12}),
                      ("trade_list_my_orders", {})], "token")
    assert gateway.calls == [("package", 12), ("orders", "token", 10)]
    assert model.binds == 0


def test_scope_hides_tools_with_missing_ids_and_locks_optional_ids():
    registry = build_trade_tool_registry(FakeGateway())
    missing = build_read_scope(registry, {"price_query"}, {}, "token")
    assert not missing.allowed_tool_names
    unauthenticated = build_read_scope(registry, {"order_query"}, {}, "")
    assert not unauthenticated.allowed_tool_names
    optional = build_read_scope(registry, {"seckill_query"}, {}, "token")
    assert optional.allowed_tool_names == frozenset({"trade_list_seckill_activities"})
    assert optional.argument_constraints["trade_list_seckill_activities"] == {"package_id": None}


@pytest.mark.parametrize("payload", [
    [],
    [{"name": "not_registered", "args": {}}],
    [{"name": "trade_list_my_orders", "args": {"page_size": 999}}],
    [{"name": "trade_list_my_orders", "args": "bad json"}],
])
def test_invalid_model_output_uses_safe_fallback(payload):
    registry = build_trade_tool_registry(FakeGateway())
    fallback = [ToolCall(name="trade_list_my_orders")]
    context = build_read_scope(registry, {"order_query"}, {}, "token")

    class Model:
        def bind_tools(self, schemas):
            return self

        def invoke(self, messages):
            return SimpleNamespace(tool_calls=payload)

    assert select_trade_tool_calls(settings=Settings(llm_router_enabled=True), registry=registry,
        message="orders", capabilities={"order_query"}, fallback_calls=fallback,
        context=context, chat_model=Model()) == fallback

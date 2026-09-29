from types import SimpleNamespace

from app.core.config import Settings
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult
from app.trade.tool_selector import select_trade_tool_calls


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
        ToolContext(),
    )

    assert executions[0].status == "invalid_arguments"
    assert gateway.calls == []


def test_tool_executor_enforces_authorization_before_gateway_call() -> None:
    gateway = FakeGateway()
    registry = build_trade_tool_registry(gateway)  # type: ignore[arg-type]

    executions = ToolExecutor(registry).execute(
        [ToolCall(name="trade_list_my_orders")],
        ToolContext(),
    )

    assert executions[0].status == "missing_authorization"
    assert gateway.calls == []


def test_llm_tool_call_is_allow_listed_and_explicit_id_wins() -> None:
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
        def bind_tools(self, schemas: list[dict[str, object]]) -> BoundModel:
            names = {
                str(schema["function"]["name"])  # type: ignore[index]
                for schema in schemas
            }
            assert "trade_get_package_snapshot" in names
            assert "trade_create_order" not in names
            return BoundModel()

    calls = select_trade_tool_calls(
        settings=Settings(
            llm_router_enabled=True,
            llm_api_key="test-key",
        ),
        registry=registry,
        message="package 12 price",
        capabilities={"price_query"},
        required_calls=required,
        chat_model=ChatModel(),
    )

    assert calls == [
        ToolCall(
            id="call-1",
            name="trade_get_package_snapshot",
            arguments={"package_id": 12},
        )
    ]

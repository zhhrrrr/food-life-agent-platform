import asyncio

import pytest

from app.core.config import Settings
from app.mcp.server import McpTradeAdapter, create_mcp_server
from app.trade.gateway_client import GatewayQueryResult


class FakeGateway:
    def __init__(self) -> None:
        self.calls: list[tuple[object, ...]] = []

    def get_package_snapshot(self, package_id: int) -> GatewayQueryResult:
        self.calls.append(("package", package_id))
        return GatewayQueryResult(
            capability="package_snapshot",
            status="success",
            data={"packageId": package_id, "stock": 8},
        )

    def list_orders(self, authorization: str, page_size: int = 10) -> GatewayQueryResult:
        self.calls.append(("orders", authorization, page_size))
        return GatewayQueryResult(
            capability="order_query",
            status="success",
            data={"orders": []},
        )


def _settings() -> Settings:
    return Settings(mcp_enabled=True, mcp_internal_token="internal-secret")


def test_mcp_adapter_requires_internal_token() -> None:
    gateway = FakeGateway()
    adapter = McpTradeAdapter(_settings(), gateway)  # type: ignore[arg-type]

    with pytest.raises(PermissionError, match="invalid MCP internal token"):
        adapter.execute(
            "trade_get_package_snapshot",
            {"package_id": 12},
            {},
        )

    assert gateway.calls == []


def test_mcp_adapter_reuses_native_tool_runtime() -> None:
    gateway = FakeGateway()
    adapter = McpTradeAdapter(_settings(), gateway)  # type: ignore[arg-type]

    result = adapter.execute(
        "trade_get_package_snapshot",
        {"package_id": 12},
        {"X-MCP-Internal-Token": "internal-secret"},
    )

    assert result["status"] == "success"
    assert result["data"] == {"packageId": 12, "stock": 8}
    assert gateway.calls == [("package", 12)]


def test_mcp_private_tool_also_requires_user_authorization() -> None:
    gateway = FakeGateway()
    adapter = McpTradeAdapter(_settings(), gateway)  # type: ignore[arg-type]

    with pytest.raises(ValueError, match="missing_authorization"):
        adapter.execute(
            "trade_list_my_orders",
            {},
            {"X-MCP-Internal-Token": "internal-secret"},
        )

    assert gateway.calls == []


def test_mcp_server_exposes_only_read_only_tool_schemas() -> None:
    server = create_mcp_server(_settings(), FakeGateway())  # type: ignore[arg-type]

    tools = asyncio.run(server.list_tools())
    names = {tool.name for tool in tools}

    assert len(tools) == 9
    assert "trade_get_package_snapshots" in names
    assert "trade_get_package_snapshot" in names
    assert not any("create" in name or "cancel" in name or "pay" in name for name in names)
    for tool in tools:
        assert tool.annotations is not None
        assert tool.annotations.read_only_hint is True
        assert tool.annotations.destructive_hint is False
        assert "ctx" not in tool.input_schema.get("properties", {})
        assert "authorization" not in tool.input_schema.get("properties", {})
        assert "internal_token" not in tool.input_schema.get("properties", {})

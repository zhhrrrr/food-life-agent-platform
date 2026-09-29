from __future__ import annotations

import hmac
from collections.abc import Mapping
from typing import Any, Literal

from mcp.server import MCPServer
from mcp.server.mcpserver import Context
from mcp.server.transport_security import TransportSecuritySettings
from mcp_types import ToolAnnotations

from app.core.config import Settings
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient

INTERNAL_TOKEN_HEADER = "x-mcp-internal-token"
READ_ONLY_ANNOTATIONS = ToolAnnotations(
    readOnlyHint=True,
    destructiveHint=False,
    idempotentHint=True,
    openWorldHint=False,
)


class McpTradeAdapter:
    def __init__(
        self,
        settings: Settings,
        gateway_client: TradeGatewayClient | None = None,
    ) -> None:
        if not settings.mcp_internal_token.strip():
            raise ValueError("MCP_INTERNAL_TOKEN is required when MCP is enabled")
        self.settings = settings
        self.registry = build_trade_tool_registry(
            gateway_client or TradeGatewayClient(settings=settings)
        )
        self.executor = ToolExecutor(self.registry, max_calls=1)

    def execute(
        self,
        name: str,
        arguments: dict[str, object],
        headers: Mapping[str, str] | None,
    ) -> dict[str, Any]:
        normalized_headers = {
            str(key).lower(): str(value) for key, value in (headers or {}).items()
        }
        supplied_token = normalized_headers.get(INTERNAL_TOKEN_HEADER, "")
        if not hmac.compare_digest(supplied_token, self.settings.mcp_internal_token):
            raise PermissionError("invalid MCP internal token")

        executions = self.executor.execute(
            [ToolCall(name=name, arguments=arguments)],
            ToolContext(authorization=normalized_headers.get("authorization", "")),
        )
        execution = executions[0]
        if not execution.succeeded:
            raise ValueError(f"tool rejected: {execution.status}")
        if not isinstance(execution.result, GatewayQueryResult):
            raise TypeError("tool returned an invalid result")
        return execution.result.model_dump(mode="json")


def create_mcp_server(
    settings: Settings,
    gateway_client: TradeGatewayClient | None = None,
) -> MCPServer:
    adapter = McpTradeAdapter(settings, gateway_client)
    server = MCPServer(
        name="food-life-trade-tools",
        description="Authenticated read-only tools for Food Life trade facts.",
        version=settings.app_version,
    )

    def execute(name: str, arguments: dict[str, object], ctx: Context) -> dict[str, Any]:
        return adapter.execute(name, arguments, ctx.headers)

    def get_package_snapshot(ctx: Context, package_id: int) -> dict[str, Any]:
        """Get the current price and stock for one package."""
        return execute(
            "trade_get_package_snapshot",
            {"package_id": package_id},
            ctx,
        )

    def list_coupon_templates(ctx: Context, limit: int = 10) -> dict[str, Any]:
        """List coupon templates available to the signed-in user."""
        return execute("trade_list_coupon_templates", {"limit": limit}, ctx)

    def list_my_coupons(
        ctx: Context,
        coupon_status: Literal["UNUSED", "USED", "EXPIRED"] = "UNUSED",
        limit: int = 10,
    ) -> dict[str, Any]:
        """List coupons owned by the signed-in user."""
        return execute(
            "trade_list_my_coupons",
            {"coupon_status": coupon_status, "limit": limit},
            ctx,
        )

    def list_group_buy_teams(
        ctx: Context,
        package_id: int,
        limit: int = 10,
    ) -> dict[str, Any]:
        """List active group-buy teams for one package."""
        return execute(
            "trade_list_group_buy_teams",
            {"package_id": package_id, "limit": limit},
            ctx,
        )

    def get_group_buy_team(
        ctx: Context,
        team_id: str,
    ) -> dict[str, Any]:
        """Get one group-buy team by ID."""
        return execute("trade_get_group_buy_team", {"team_id": team_id}, ctx)

    def list_my_group_buy_orders(
        ctx: Context,
        page_size: int = 10,
    ) -> dict[str, Any]:
        """List group-buy orders owned by the signed-in user."""
        return execute(
            "trade_list_my_group_buy_orders",
            {"page_size": page_size},
            ctx,
        )

    def get_order(
        ctx: Context,
        order_id: int,
    ) -> dict[str, Any]:
        """Get current details for one order owned by the signed-in user."""
        return execute("trade_get_order", {"order_id": order_id}, ctx)

    def list_my_orders(
        ctx: Context,
        page_size: int = 10,
    ) -> dict[str, Any]:
        """List recent orders owned by the signed-in user."""
        return execute("trade_list_my_orders", {"page_size": page_size}, ctx)

    for name, function in (
        ("trade_get_package_snapshot", get_package_snapshot),
        ("trade_list_coupon_templates", list_coupon_templates),
        ("trade_list_my_coupons", list_my_coupons),
        ("trade_list_group_buy_teams", list_group_buy_teams),
        ("trade_get_group_buy_team", get_group_buy_team),
        ("trade_list_my_group_buy_orders", list_my_group_buy_orders),
        ("trade_get_order", get_order),
        ("trade_list_my_orders", list_my_orders),
    ):
        server.add_tool(
            function,
            name=name,
            annotations=READ_ONLY_ANNOTATIONS,
            structured_output=True,
        )
    return server


def build_mcp_asgi_app(server: MCPServer, settings: Settings) -> Any:
    security = None
    if settings.mcp_allowed_hosts or settings.mcp_allowed_origins:
        security = TransportSecuritySettings(
            allowed_hosts=settings.mcp_allowed_hosts,
            allowed_origins=settings.mcp_allowed_origins,
        )
    return server.streamable_http_app(
        streamable_http_path="/",
        json_response=True,
        stateless_http=True,
        transport_security=security,
    )

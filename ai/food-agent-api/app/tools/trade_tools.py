from __future__ import annotations

from typing import Literal, cast

from pydantic import BaseModel, ConfigDict, Field

from app.tools.models import ToolSpec
from app.tools.registry import ToolRegistry
from app.trade.gateway_client import TradeGatewayClient


class TradeToolArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")


class PackageIdArgs(TradeToolArgs):
    package_id: int = Field(gt=0)


class CouponTemplatesArgs(TradeToolArgs):
    limit: int = Field(default=10, ge=1, le=50)


class MyCouponsArgs(TradeToolArgs):
    coupon_status: Literal["UNUSED", "USED", "EXPIRED"] = "UNUSED"
    limit: int = Field(default=10, ge=1, le=50)


class GroupBuyTeamsArgs(TradeToolArgs):
    package_id: int = Field(gt=0)
    limit: int = Field(default=10, ge=1, le=50)


class GroupBuyTeamArgs(TradeToolArgs):
    team_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$")


class PageSizeArgs(TradeToolArgs):
    page_size: int = Field(default=10, ge=1, le=50)


class OrderIdArgs(TradeToolArgs):
    order_id: int = Field(gt=0)


def build_trade_tool_registry(client: TradeGatewayClient) -> ToolRegistry:
    return ToolRegistry(
        [
            ToolSpec(
                name="trade_get_package_snapshot",
                description="Get current price and stock for one package ID.",
                args_schema=PackageIdArgs,
                capabilities=frozenset({"price_query", "stock_query", "order_preview"}),
                handler=lambda args, _ctx: client.get_package_snapshot(
                    cast(PackageIdArgs, args).package_id
                ),
            ),
            ToolSpec(
                name="trade_list_coupon_templates",
                description="List coupon templates currently available to the signed-in user.",
                args_schema=CouponTemplatesArgs,
                capabilities=frozenset({"coupon_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.list_coupon_templates(
                    ctx.authorization,
                    cast(CouponTemplatesArgs, args).limit,
                ),
            ),
            ToolSpec(
                name="trade_list_my_coupons",
                description="List coupons owned by the signed-in user.",
                args_schema=MyCouponsArgs,
                capabilities=frozenset({"coupon_query", "order_preview"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.list_my_coupons(
                    ctx.authorization,
                    cast(MyCouponsArgs, args).coupon_status,
                    cast(MyCouponsArgs, args).limit,
                ),
            ),
            ToolSpec(
                name="trade_list_group_buy_teams",
                description="List active group-buy teams for one package ID.",
                args_schema=GroupBuyTeamsArgs,
                capabilities=frozenset({"group_buy_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.list_group_buy_teams(
                    cast(GroupBuyTeamsArgs, args).package_id,
                    ctx.authorization,
                    cast(GroupBuyTeamsArgs, args).limit,
                ),
            ),
            ToolSpec(
                name="trade_get_group_buy_team",
                description="Get one group-buy team by team ID.",
                args_schema=GroupBuyTeamArgs,
                capabilities=frozenset({"group_buy_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.get_group_buy_team(
                    cast(GroupBuyTeamArgs, args).team_id,
                    ctx.authorization,
                ),
            ),
            ToolSpec(
                name="trade_list_my_group_buy_orders",
                description="List group-buy orders owned by the signed-in user.",
                args_schema=PageSizeArgs,
                capabilities=frozenset({"group_buy_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.list_my_group_buy_orders(
                    ctx.authorization,
                    cast(PageSizeArgs, args).page_size,
                ),
            ),
            ToolSpec(
                name="trade_get_order",
                description="Get current details for one order ID owned by the signed-in user.",
                args_schema=OrderIdArgs,
                capabilities=frozenset({"order_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.get_order(
                    cast(OrderIdArgs, args).order_id,
                    ctx.authorization,
                ),
            ),
            ToolSpec(
                name="trade_list_my_orders",
                description="List recent orders owned by the signed-in user.",
                args_schema=PageSizeArgs,
                capabilities=frozenset({"order_query"}),
                requires_authorization=True,
                handler=lambda args, ctx: client.list_orders(
                    ctx.authorization,
                    cast(PageSizeArgs, args).page_size,
                ),
            ),
        ]
    )

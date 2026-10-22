from __future__ import annotations

from typing import Annotated, Literal, cast

from pydantic import BaseModel, ConfigDict, Field

from app.tools.models import ToolSpec
from app.tools.registry import ToolRegistry
from app.trade.gateway_client import TradeGatewayClient


class TradeToolArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class PackageIdArgs(TradeToolArgs):
    package_id: int = Field(gt=0)


class PackageIdsArgs(TradeToolArgs):
    package_ids: list[Annotated[int, Field(gt=0)]] = Field(min_length=1, max_length=50)


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


class ActivityIdArgs(TradeToolArgs):
    activity_id: int = Field(gt=0)


class SeckillActivitiesArgs(TradeToolArgs):
    package_id: int | None = Field(default=None, gt=0)
    limit: int = Field(default=20, ge=1, le=50)


class RequestNoArgs(TradeToolArgs):
    request_no: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")


class PaymentNoArgs(TradeToolArgs):
    pay_order_no: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")


def build_trade_tool_registry(client: TradeGatewayClient) -> ToolRegistry:
    return ToolRegistry(
        [
            ToolSpec(name="trade_get_group_buy_activity", description="Read actual group price, activity stock, dates and target count for a package.",
                args_schema=PackageIdArgs, capabilities=frozenset({"group_buy_query", "purchase_options"}), requires_authorization=True,
                handler=lambda args, ctx: client.get_group_buy_activity(args.package_id, ctx.authorization)),
            ToolSpec(name="trade_list_seckill_activities", description="List actual seckill activities, optionally filtered by package. Availability is advisory, not a reservation.",
                args_schema=SeckillActivitiesArgs, capabilities=frozenset({"seckill_query", "purchase_options"}), requires_authorization=True,
                handler=lambda args, ctx: client.list_seckill_activities(ctx.authorization, args.package_id, args.limit)),
            ToolSpec(name="trade_get_seckill_activity", description="Read actual price, stock, dates and limits for a specific seckill activity ID.",
                args_schema=ActivityIdArgs, capabilities=frozenset({"seckill_query"}), requires_authorization=True,
                handler=lambda args, ctx: client.get_seckill_activity(args.activity_id, ctx.authorization)),
            ToolSpec(name="trade_get_seckill_request", description="Read signed-in user's async seckill request result. Only SUCCESS with orderId means an order exists.",
                args_schema=RequestNoArgs, capabilities=frozenset({"seckill_request"}), requires_authorization=True,
                handler=lambda args, ctx: client.get_seckill_request(args.request_no, ctx.authorization)),
            ToolSpec(name="trade_get_payment", description="Read signed-in user's payment status; PREPARED is not payment success.",
                args_schema=PaymentNoArgs, capabilities=frozenset({"payment_query"}), requires_authorization=True,
                handler=lambda args, ctx: client.get_payment(args.pay_order_no, ctx.authorization)),
            ToolSpec(
                name="trade_get_package_snapshot",
                description="Get current price, stock, status and package/shop sales for one package ID.",
                args_schema=PackageIdArgs,
                capabilities=frozenset({"price_query", "stock_query", "sales_query", "order_preview"}),
                handler=lambda args, _ctx: client.get_package_snapshot(
                    cast(PackageIdArgs, args).package_id
                ),
            ),
            ToolSpec(
                name="trade_get_package_snapshots",
                description="Batch-read current price, stock, status and package/shop sales for 1 to 50 candidate package IDs.",
                args_schema=PackageIdsArgs,
                capabilities=frozenset({"price_query", "stock_query", "sales_query", "package_recommendation"}),
                handler=lambda args, _ctx: client.get_package_snapshots(
                    cast(PackageIdsArgs, args).package_ids
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

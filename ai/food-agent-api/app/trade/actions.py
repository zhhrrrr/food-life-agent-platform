"""Fixed Java write endpoints, isolated from the read-only MCP registry."""
from __future__ import annotations

import httpx
from pydantic import BaseModel, ConfigDict, Field

from app.core.config import Settings
from app.tools.models import ToolCall, ToolContext, ToolSpec
from app.tools.registry import ToolRegistry
from app.tools.budget import bounded_http_timeout

class ActionResult(BaseModel):
    status: str
    data: dict = Field(default_factory=dict)
    message: str = ""


class OrderArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    order_id: int = Field(gt=0)


class CreateArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    package_id: int = Field(gt=0)
    quantity: int = Field(ge=1, le=99)
    user_coupon_id: int | None = Field(default=None, gt=0)
    expected_unit_price: int | None = Field(default=None, ge=0)


class RefundArgs(OrderArgs):
    refund_reason: str = Field(min_length=1, max_length=255)


class GroupBuyArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    package_id: int = Field(gt=0)
    quantity: int = Field(ge=1, le=1)
    team_id: str | None = Field(default=None, pattern=r"^[A-Za-z0-9_-]{1,64}$")
    expected_unit_price: int = Field(ge=0)
    expected_activity_id: int = Field(gt=0)


class SeckillArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    activity_id: int = Field(gt=0)
    quantity: int = Field(ge=1, le=1)
    expected_unit_price: int = Field(ge=0)


class PaymentArgs(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    pay_order_no: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")


class TradeActionClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def create(self, args: CreateArgs, context: ToolContext) -> ActionResult:
        result = self._post("/api/trade/orders/normal", {
            "packageId": args.package_id, "quantity": args.quantity,
            "userCouponId": args.user_coupon_id, "requestId": context.operation_id,
            "expectedUnitPrice": args.expected_unit_price,
        }, context)
        return self._creation_result(result, "orderId")

    def pay(self, args: OrderArgs, context: ToolContext) -> ActionResult:
        return self._post(f"/api/trade/pay/orders/{args.order_id}/prepare", {
            "source": "FOOD_LIFE_AGENT", "channel": "ALIPAY_SANDBOX",
        }, context)

    def group_buy(self, args: GroupBuyArgs, context: ToolContext) -> ActionResult:
        result = self._post("/api/trade/orders/group-buy", {"packageId": args.package_id, "quantity": args.quantity,
            "teamId": args.team_id, "requestId": context.operation_id, "expectedUnitPrice": args.expected_unit_price,
            "expectedActivityId": args.expected_activity_id}, context)
        return self._creation_result(result, "orderId")

    def seckill(self, args: SeckillArgs, context: ToolContext) -> ActionResult:
        result = self._post("/api/trade/orders/seckill/async", {"activityId": args.activity_id, "quantity": args.quantity,
            "requestId": context.operation_id, "expectedUnitPrice": args.expected_unit_price}, context)
        return self._creation_result(result, "requestNo")

    @staticmethod
    def _creation_result(result: ActionResult, key: str) -> ActionResult:
        import re
        value = result.data.get(key)
        valid = (type(value) is int and value > 0) if key == "orderId" else isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", value)
        if result.status == "success" and not valid:
            return ActionResult(status="uncertain", message="交易服务返回缺少有效结果标识，请恢复同一工作流核对。")
        return result

    def sync_payment(self, args: PaymentArgs, context: ToolContext) -> ActionResult:
        return self._post(f"/api/trade/pay/orders/{args.pay_order_no}/sync", None, context)

    def cancel(self, args: OrderArgs, context: ToolContext) -> ActionResult:
        return self._post(f"/api/trade/orders/{args.order_id}/cancel", None, context)

    def refund(self, args: RefundArgs, context: ToolContext) -> ActionResult:
        return self._post(f"/api/trade/orders/{args.order_id}/refund/apply", {
            "source": "FOOD_LIFE_AGENT", "channel": "ALIPAY_SANDBOX",
            "refundReason": args.refund_reason,
        }, context)

    def _post(self, path: str, body: dict | None, context: ToolContext) -> ActionResult:
        if not context.authorization.strip():
            return ActionResult(status="unauthorized", message="请先登录。")
        try:
            response = httpx.post(
                self.settings.java_gateway_base_url.rstrip("/") + path,
                json=body, headers={"Authorization": context.authorization, "Idempotency-Key": context.operation_id},
                timeout=bounded_http_timeout(self.settings.trade_gateway_timeout_seconds), trust_env=False,
            )
            if response.status_code in (401, 403):
                return ActionResult(status="unauthorized", message="登录已失效或无权操作该订单。")
            if response.status_code >= 500:
                return ActionResult(status="uncertain", message="交易服务未能确认结果，请查询订单后恢复。")
            if response.status_code != 200:
                return ActionResult(status="failed", message="交易请求被拒绝。")
            payload = response.json()
            if not isinstance(payload, dict):
                raise ValueError("invalid envelope")
            if str(payload.get("code")) != "0000":
                status = "uncertain" if str(payload.get("code")) in {"500", "503"} else "failed"
                return ActionResult(status=status, message=str(payload.get("message") or "业务操作失败。")[:500])
            data = payload.get("data")
            if not isinstance(data, dict):
                raise ValueError("invalid trade result")
            return ActionResult(status="success", data=data)
        except (httpx.HTTPError, ValueError):
            # Do not retry writes here: a response loss does not prove rollback.
            return ActionResult(status="uncertain", message="交易结果暂未确认，请使用同一工作流恢复。")


ACTION_TOOLS = {"create": "trade_create_order", "group_buy": "trade_create_group_buy", "seckill": "trade_submit_seckill",
                "pay": "trade_prepare_alipay", "cancel": "trade_cancel_order", "refund": "trade_apply_refund", "sync_payment": "trade_sync_payment"}


def build_action_registry(client: TradeActionClient) -> ToolRegistry:
    return ToolRegistry([
        ToolSpec(name=ACTION_TOOLS[action], description=description, args_schema=schema,
                 handler=handler, capabilities=frozenset({action}), requires_authorization=True, risk="write")
        for action, description, schema, handler in [
            ("create", "Create a normal package order with confirmed quantity and optional coupon.", CreateArgs, client.create),
            ("group_buy", "Open a group-buy team (team_id null) or join the exact confirmed team; quantity must be 1.", GroupBuyArgs,
             lambda args, ctx: client.group_buy(args, ctx)),
            ("seckill", "Submit an async seckill request, quantity 1. A requestNo is not a completed order.", SeckillArgs,
             lambda args, ctx: client.seckill(args, ctx)),
            ("sync_payment", "Reconcile a confirmed payment with Alipay; never fabricate payment success.", PaymentArgs,
             lambda args, ctx: client.sync_payment(args, ctx)),
            ("pay", "Prepare an Alipay SANDBOX checkout, without asserting payment success.", OrderArgs, client.pay),
            ("cancel", "Cancel the confirmed unpaid order.", OrderArgs, client.cancel),
            ("refund", "Apply for refund for the confirmed order and reason; does not approve a refund.", RefundArgs, client.refund),
        ]
    ])


def build_action_call(action: str, params: dict, registry: ToolRegistry) -> ToolCall:
    """Build the deterministic write proposal; HITL decides whether it can run."""
    if action == "create":
        action = {"NORMAL": "create", "GROUP_BUY": "group_buy", "SECKILL": "seckill"}[params.get("trade_type") or "NORMAL"]
    name = ACTION_TOOLS[action]
    spec = registry.get(name)
    args = spec.args_schema.model_validate({key: params[key] for key in spec.args_schema.model_fields if key in params})
    return ToolCall(name=name, arguments=args.model_dump())

"""Read-only purchase facts. Every business read goes through registered tools."""
from __future__ import annotations

from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult


class PurchaseFactsError(ValueError):
    pass


def purchase_advice_summary(data: dict) -> str:
    recommendation = data["recommended"]
    if recommendation:
        label = {"NORMAL": "普通购买", "GROUP_BUY": "拼团购买", "SECKILL": "秒杀购买"}[recommendation["trade_type"]]
        response = (f"按当前偏好，建议{label}，参考金额{recommendation['price_cents'] / 100:.2f}元。"
                    + recommendation.get("conditions", ""))
    else:
        response = "当前没有能确认满足预算和等待偏好的购买方案。"
    return response + data["note"]


class PurchaseReader:
    def __init__(self, client, settings):
        self.registry = build_trade_tool_registry(client)
        self.executor = ToolExecutor(self.registry, settings=settings)

    def read(self, name: str, authorization: str, **arguments) -> GatewayQueryResult:
        return self.read_many([(name, arguments)], authorization)[name]

    def read_many(self, calls: list[tuple[str, dict]], authorization: str) -> dict[str, GatewayQueryResult]:
        required = [ToolCall(name=name, arguments=args) for name, args in calls]
        # This is a fixed business read set, not an LLM planning decision.
        executions = self.executor.execute(required, ToolContext(
            authorization=authorization,
            allowed_tool_names=frozenset(call.name for call in required),
            argument_constraints={call.name: call.arguments for call in required},
        ))
        by_name = {item.call.name: item for item in executions}
        results = {}
        for call in required:
            item = by_name.get(call.name)
            results[call.name] = (item.result if item and item.succeeded and isinstance(item.result, GatewayQueryResult)
                else GatewayQueryResult(capability=call.name, status="unavailable",
                    message="交易查询未完成，请稍后重试。", error_code=item.error_code if item else "TOOL_CALL_LIMIT"))
        return results

    def fact(self, name: str, authorization: str, **arguments) -> dict:
        result = self.read(name, authorization, **arguments)
        if not result.has_data or not isinstance(result.data, dict):
            raise PurchaseFactsError(result.message or "未取得实时交易信息。")
        return result.data

    def preview(self, intent, authorization: str) -> dict:
        mode = intent.trade_type or "NORMAL"  # Legacy persisted ordinary workflows.
        if mode != "NORMAL" and intent.quantity != 1:
            raise PurchaseFactsError("当前拼团和秒杀每次只能购买1份，请重新明确购买数量。")
        if mode != "NORMAL" and intent.user_coupon_id is not None:
            raise PurchaseFactsError("当前拼团和秒杀接口不支持叠加优惠券，请先明确不使用优惠券。")
        if mode == "SECKILL":
            activity = self.fact("trade_get_seckill_activity", authorization, activity_id=intent.activity_id)
            if intent.package_id is not None and activity.get("packageId") != intent.package_id:
                raise PurchaseFactsError("秒杀活动与所选套餐不一致。")
            if activity.get("canBuy") is not True:
                raise PurchaseFactsError("秒杀活动当前不可购买，可能未开始、已结束或库存不足。")
            package_id = activity.get("packageId")
            price = activity.get("seckillPrice")
            extra = {"activity_id": intent.activity_id, "valid_end_time": activity.get("validEndTime"),
                     "user_take_limit": activity.get("userTakeLimit"), "stock_note": "最终以提交时的库存预占结果为准。"}
        else:
            package_id = intent.package_id
            extra = {}
            if mode == "GROUP_BUY":
                activity = self.fact("trade_get_group_buy_activity", authorization, package_id=package_id)
                if intent.activity_id is not None and activity.get("activityId") != intent.activity_id:
                    raise PurchaseFactsError("当前拼团活动与指定活动不一致，请重新选择。")
                if activity.get("canBuy") is not True:
                    raise PurchaseFactsError("拼团活动当前不可购买。")
                price = activity.get("groupPrice")
                extra = {"activity_id": activity.get("activityId"), "group_mode": intent.group_mode,
                         "team_id": intent.team_id, "target_count": activity.get("targetCount"),
                         "valid_end_time": activity.get("validEndTime"), "group_note": "付款成功后仍需等待团队成团。"}
                if intent.group_mode == "join":
                    team = self.fact("trade_get_group_buy_team", authorization, team_id=intent.team_id)
                    if team.get("packageId") != package_id or team.get("activityId") != activity.get("activityId") or team.get("canJoin") is not True:
                        raise PurchaseFactsError("指定拼团队伍不匹配、已满或已过期，请重新选择。")
                    extra.update(remaining_count=team.get("remainingCount"), valid_end_time=team.get("validEndTime"))
        package = self.fact("trade_get_package_snapshot", authorization, package_id=package_id)
        if package.get("packageStatus") is not None and package["packageStatus"] != 1:
            raise PurchaseFactsError("套餐已下架或不可购买。")
        if mode == "NORMAL":
            price = package.get("price")
        if type(price) is not int or price < 0:
            raise PurchaseFactsError("实时购买价格无效，请稍后重试。")
        if mode != "SECKILL" and isinstance(package.get("stock"), int) and package["stock"] < intent.quantity:
            raise PurchaseFactsError("当前套餐库存不足。")
        return {"trade_type": mode, "package_id": package_id, "package_name": package.get("packageName", ""),
                "quantity": intent.quantity, "unit_price_cents": price, "estimated_total_cents": price * intent.quantity,
                "user_coupon_id": intent.user_coupon_id,
                "amount_note": "优惠前估算，以Java下单结果为准。" if mode == "NORMAL" else "活动价格，以Java最终校验为准。",
                **extra}

    def query(self, intent, authorization: str) -> GatewayQueryResult:
        if intent.request_no:
            return self.read("trade_get_seckill_request", authorization, request_no=intent.request_no)
        if intent.pay_order_no:
            return self.read("trade_get_payment", authorization, pay_order_no=intent.pay_order_no)
        if intent.query_kind == "seckill_activities":
            if intent.activity_id:
                return self.read("trade_get_seckill_activity", authorization, activity_id=intent.activity_id)
            return self.read("trade_list_seckill_activities", authorization, package_id=intent.package_id)
        if intent.query_kind == "group_activity":
            return self.read("trade_get_group_buy_activity", authorization, package_id=intent.package_id)
        if intent.query_kind == "group_teams":
            return self.read("trade_list_group_buy_teams", authorization, package_id=intent.package_id)
        return self.read("trade_get_order", authorization, order_id=intent.order_id) if intent.order_id else self.read("trade_list_my_orders", authorization)

    def advise(self, intent, authorization: str) -> dict:
        results = self.read_many([
            ("trade_get_package_snapshot", {"package_id": intent.package_id}),
            ("trade_get_group_buy_activity", {"package_id": intent.package_id}),
            ("trade_list_group_buy_teams", {"package_id": intent.package_id}),
            ("trade_list_seckill_activities", {"package_id": intent.package_id}),
            ("trade_list_my_coupons", {})], authorization)
        options = []
        package = results["trade_get_package_snapshot"]
        package_available = (package.has_data and isinstance(package.data, dict)
                             and package.data.get("packageStatus", 1) == 1)
        quantity = intent.quantity or 1
        if package_available and type(package.data.get("price")) is int and package.data.get("stock", 0) >= quantity:
            options.append({"trade_type": "NORMAL", "price_cents": package.data["price"] * quantity,
                "message": f"普通下单套餐{intent.package_id}，数量{quantity}", "conditions": "优惠前金额，优惠券资格由Java校验。"})
        group = results["trade_get_group_buy_activity"]
        if package_available and quantity == 1 and group.has_data and isinstance(group.data, dict) and group.data.get("canBuy") is True and type(group.data.get("groupPrice")) is int:
            options.append({"trade_type": "GROUP_BUY", "price_cents": group.data["groupPrice"],
                "message": f"开团套餐{intent.package_id}，数量1", "conditions": "需要等待成团，不能保证今天可核销。"})
            teams = results["trade_list_group_buy_teams"]
            for team in (teams.data.get("teams", []) if teams.has_data and isinstance(teams.data, dict) else []):
                if team.get("canJoin") is True and team.get("packageId") == intent.package_id and team.get("activityId") == group.data.get("activityId"):
                    options.append({"trade_type": "GROUP_BUY", "price_cents": group.data["groupPrice"], "team_id": team["teamId"],
                        "message": f"参团套餐{intent.package_id}，数量1，团队{team['teamId']}",
                        "conditions": f"已有团队还剩{team.get('remainingCount')}个名额，截止{team.get('validEndTime')}；仍需等待成团。"})
        seckill = results["trade_list_seckill_activities"]
        activities = seckill.data.get("activities", []) if seckill.has_data and isinstance(seckill.data, dict) else []
        for activity in activities:
            if package_available and quantity == 1 and activity.get("packageId") == intent.package_id and activity.get("canBuy") is True and type(activity.get("seckillPrice")) is int:
                options.append({"trade_type": "SECKILL", "activity_id": activity["activityId"], "price_cents": activity["seckillPrice"],
                    "message": f"参加秒杀活动{activity['activityId']}，数量1", "conditions": "限购及库存以提交结果为准。"})
        for option in options:
            option["matches_preferences"] = ((intent.willing_to_wait is not False or option["trade_type"] != "GROUP_BUY")
                and (intent.budget_cents is None or option["price_cents"] <= intent.budget_cents))
        options.sort(key=lambda item: (not item["matches_preferences"], item["price_cents"]))
        return {"options": options, "recommended": next((item for item in options if item["matches_preferences"]), None),
                "facts": {name: result.model_dump() for name, result in results.items()}, "previewOnly": True,
                "note": "比较活动标价及普通优惠前金额；未自动扣除优惠券。没有创建订单，所有购买仍需确认。"}

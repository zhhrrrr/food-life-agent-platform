import logging
from typing import Any, Literal
from urllib.parse import quote

import httpx
from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.tools.budget import bounded_http_timeout

logger = logging.getLogger("food_agent.trade.gateway")

GatewayQueryStatus = Literal[
    "success",
    "missing_parameter",
    "missing_authorization",
    "not_found",
    "unavailable",
]


class GatewayQueryResult(BaseModel):
    capability: str
    status: GatewayQueryStatus
    endpoint: str = ""
    data: Any = None
    message: str = ""
    reference_id: str = ""
    reference_title: str = ""
    retryable: bool = False
    error_code: str = ""

    @property
    def has_data(self) -> bool:
        return self.status == "success" and self.data is not None


class TradeGatewayClient:
    """Allow-listed Java Gateway reads for TradeAgent.

    The public API intentionally contains no generic request method and no write
    operation, so the agent cannot create, cancel, pay, or refund an order.
    """

    def __init__(
        self,
        settings: Settings | None = None,
        timeout_seconds: float = 5.0,
    ) -> None:
        self.settings = settings or get_settings()
        self.timeout_seconds = timeout_seconds

    def get_package_snapshot(self, package_id: int) -> GatewayQueryResult:
        return self._get(
            capability="package_snapshot",
            path=f"/api/package/trade-snapshot/{package_id}",
            reference_id=f"package:{package_id}:trade-snapshot",
            reference_title=f"套餐 {package_id} 实时交易快照",
        )

    def list_coupon_templates(
        self,
        authorization: str,
        limit: int = 10,
    ) -> GatewayQueryResult:
        return self._authorized_get(
            capability="coupon_query",
            path="/api/trade/coupons/templates",
            authorization=authorization,
            params={"limit": limit},
            reference_id="coupon:available-templates",
            reference_title="当前可领取优惠券",
        )

    def get_package_snapshots(self, package_ids: list[int]) -> GatewayQueryResult:
        return self._get(
            capability="package_snapshots",
            path="/api/package/trade-snapshots",
            params={"packageIds": ",".join(str(item) for item in dict.fromkeys(package_ids))},
            reference_id="package:batch:trade-snapshots",
            reference_title="候选套餐当前交易信息",
        )

    def list_my_coupons(
        self,
        authorization: str,
        coupon_status: str = "UNUSED",
        limit: int = 10,
    ) -> GatewayQueryResult:
        return self._authorized_get(
            capability="coupon_query",
            path="/api/trade/coupons/mine",
            authorization=authorization,
            params={"couponStatus": coupon_status, "limit": limit},
            reference_id="coupon:mine",
            reference_title="当前用户优惠券",
        )

    def list_group_buy_teams(
        self,
        package_id: int,
        authorization: str,
        limit: int = 10,
    ) -> GatewayQueryResult:
        return self._authorized_get(
            capability="group_buy_query",
            path="/api/trade/group-buy/teams",
            authorization=authorization,
            params={"packageId": package_id, "limit": limit},
            reference_id=f"group-buy:package:{package_id}",
            reference_title=f"套餐 {package_id} 可参与拼团",
        )

    def get_group_buy_team(
        self,
        team_id: str,
        authorization: str,
    ) -> GatewayQueryResult:
        safe_team_id = quote(team_id, safe="")
        return self._authorized_get(
            capability="group_buy_query",
            path=f"/api/trade/group-buy/teams/{safe_team_id}",
            authorization=authorization,
            reference_id=f"group-buy:team:{team_id}",
            reference_title=f"拼团队伍 {team_id}",
        )

    def list_my_group_buy_orders(
        self,
        authorization: str,
        page_size: int = 10,
    ) -> GatewayQueryResult:
        return self._authorized_get(
            capability="group_buy_query",
            path="/api/trade/group-buy/orders",
            authorization=authorization,
            params={"pageSize": page_size},
            reference_id="group-buy:my-orders",
            reference_title="当前用户拼团订单",
        )

    def get_order(self, order_id: int, authorization: str) -> GatewayQueryResult:
        return self._authorized_get(
            capability="order_query",
            path=f"/api/trade/orders/{order_id}",
            authorization=authorization,
            reference_id=f"order:{order_id}",
            reference_title=f"订单 {order_id} 实时详情",
        )

    def list_orders(
        self,
        authorization: str,
        page_size: int = 10,
    ) -> GatewayQueryResult:
        return self._authorized_get(
            capability="order_query",
            path="/api/trade/orders",
            authorization=authorization,
            params={"pageSize": page_size},
            reference_id="order:mine",
            reference_title="当前用户订单列表",
        )

    def get_group_buy_activity(self, package_id: int, authorization: str) -> GatewayQueryResult:
        return self._authorized_get(capability="group_buy_query", path=f"/api/trade/group-buy/activities/package/{package_id}",
            authorization=authorization, reference_id=f"group-buy:activity:package:{package_id}", reference_title="拼团活动价格与购买条件")

    def list_seckill_activities(self, authorization: str, package_id: int | None = None, limit: int = 20) -> GatewayQueryResult:
        return self._authorized_get(capability="seckill_query", path="/api/trade/seckill/activities", authorization=authorization,
            params={**({"packageId": package_id} if package_id is not None else {}), "limit": limit},
            reference_id=f"seckill:activities:{package_id or 'all'}", reference_title="秒杀活动价格、时间和库存")

    def get_seckill_activity(self, activity_id: int, authorization: str) -> GatewayQueryResult:
        return self._authorized_get(capability="seckill_query", path=f"/api/trade/seckill/activities/{activity_id}",
            authorization=authorization, reference_id=f"seckill:activity:{activity_id}", reference_title="指定秒杀活动实时信息")

    def get_seckill_request(self, request_no: str, authorization: str) -> GatewayQueryResult:
        return self._authorized_get(capability="seckill_request", path=f"/api/trade/seckill/order-requests/{quote(request_no, safe='')}",
            authorization=authorization, reference_id=f"seckill:request:{request_no}", reference_title="秒杀请求处理结果")

    def get_payment(self, pay_order_no: str, authorization: str) -> GatewayQueryResult:
        return self._authorized_get(capability="payment_query", path=f"/api/trade/pay/orders/{quote(pay_order_no, safe='')}",
            authorization=authorization, reference_id=f"payment:{pay_order_no}", reference_title="支付单当前状态")

    def _authorized_get(
        self,
        *,
        capability: str,
        path: str,
        authorization: str,
        params: dict[str, object] | None = None,
        reference_id: str,
        reference_title: str,
    ) -> GatewayQueryResult:
        if not authorization.strip():
            return GatewayQueryResult(
                capability=capability,
                status="missing_authorization",
                endpoint=path,
                message="该查询需要登录用户的 Authorization。",
            )
        return self._get(
            capability=capability,
            path=path,
            authorization=authorization,
            params=params,
            reference_id=reference_id,
            reference_title=reference_title,
        )

    def _get(
        self,
        *,
        capability: str,
        path: str,
        authorization: str = "",
        params: dict[str, object] | None = None,
        reference_id: str,
        reference_title: str,
    ) -> GatewayQueryResult:
        url = f"{self.settings.java_gateway_base_url.rstrip('/')}{path}"
        headers = {"authorization": authorization} if authorization.strip() else {}
        try:
            response = httpx.get(
                url,
                headers=headers,
                params=params,
                timeout=bounded_http_timeout(self.timeout_seconds),
                trust_env=False,
            )
        except httpx.TransportError:
            logger.exception("trade_gateway_request_failed capability=%s path=%s", capability, path)
            return GatewayQueryResult(
                capability=capability,
                status="unavailable",
                endpoint=path,
                message="Java Gateway 查询暂不可用。",
                retryable=True,
                error_code="TOOL_NETWORK_ERROR",
            )
        except httpx.HTTPError:
            return GatewayQueryResult(capability=capability, status="unavailable", endpoint=path,
                message="Java Gateway 响应无效。", error_code="TOOL_RESPONSE_INVALID")

        if response.status_code in (401, 403):
            return GatewayQueryResult(capability=capability, status="missing_authorization",
                endpoint=path, message="登录失效或没有查询权限。", error_code="TOOL_AUTH_DENIED")

        if response.status_code == 404:
            return GatewayQueryResult(
                capability=capability,
                status="not_found",
                endpoint=path,
                message="未查询到对应业务数据。",
            )
        if response.status_code != 200:
            logger.warning(
                "trade_gateway_unavailable capability=%s path=%s status_code=%s",
                capability,
                path,
                response.status_code,
            )
            return GatewayQueryResult(
                capability=capability,
                status="unavailable",
                endpoint=path,
                message="Java Gateway 查询暂不可用。",
                retryable=response.status_code in {429, 500, 502, 503, 504},
                error_code="TOOL_UPSTREAM_TRANSIENT" if response.status_code in {429, 500, 502, 503, 504} else "TOOL_UPSTREAM_REJECTED",
            )

        try:
            payload = response.json()
            if not isinstance(payload, dict):
                raise TypeError("response payload must be an object")
            code = str(payload.get("code", ""))
            if code != "0000":
                status: GatewayQueryStatus = "not_found" if code == "404" else "unavailable"
                return GatewayQueryResult(
                    capability=capability,
                    status=status,
                    endpoint=path,
                    message=str(payload.get("message") or "Java 业务查询失败。"),
                    retryable=code in {"429", "500", "502", "503", "504"},
                    error_code="TOOL_UPSTREAM_TRANSIENT" if code in {"429", "500", "502", "503", "504"} else "TOOL_BUSINESS_REJECTED",
                )
            data = payload.get("data")
        except (TypeError, ValueError):
            logger.exception("trade_gateway_invalid_payload capability=%s path=%s", capability, path)
            return GatewayQueryResult(
                capability=capability,
                status="unavailable",
                endpoint=path,
                message="Java Gateway 返回了无效数据。",
                error_code="TOOL_RESPONSE_INVALID",
            )

        return GatewayQueryResult(
            capability=capability,
            status="success",
            endpoint=path,
            data=data,
            reference_id=reference_id,
            reference_title=reference_title,
        )

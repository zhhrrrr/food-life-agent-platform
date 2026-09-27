import logging
from typing import Any, Literal
from urllib.parse import quote

import httpx
from pydantic import BaseModel

from app.core.config import Settings, get_settings

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
                timeout=self.timeout_seconds,
                trust_env=False,
            )
        except httpx.HTTPError:
            logger.exception("trade_gateway_request_failed capability=%s path=%s", capability, path)
            return GatewayQueryResult(
                capability=capability,
                status="unavailable",
                endpoint=path,
                message="Java Gateway 查询暂不可用。",
            )

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
                )
            data = payload.get("data")
        except (TypeError, ValueError):
            logger.exception("trade_gateway_invalid_payload capability=%s path=%s", capability, path)
            return GatewayQueryResult(
                capability=capability,
                status="unavailable",
                endpoint=path,
                message="Java Gateway 返回了无效数据。",
            )

        return GatewayQueryResult(
            capability=capability,
            status="success",
            endpoint=path,
            data=data,
            reference_id=reference_id,
            reference_title=reference_title,
        )

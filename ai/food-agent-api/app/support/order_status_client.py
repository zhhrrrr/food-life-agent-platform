import logging
from typing import Literal

import httpx
from pydantic import BaseModel

from app.core.config import Settings, get_settings

logger = logging.getLogger("food_agent.support.order_status")


class OrderStatusSnapshot(BaseModel):
    order_id: int
    order_no: str = ""
    trade_type: str = ""
    order_status: str


class OrderStatusLookup(BaseModel):
    status: Literal[
        "not_requested",
        "missing_order_id",
        "missing_authorization",
        "not_found",
        "unavailable",
        "success",
    ]
    order_id: int | None = None
    snapshot: OrderStatusSnapshot | None = None

    @property
    def has_snapshot(self) -> bool:
        return self.snapshot is not None


class JavaOrderStatusClient:
    def __init__(
        self,
        settings: Settings | None = None,
        timeout_seconds: float = 5.0,
    ) -> None:
        self.settings = settings or get_settings()
        self.timeout_seconds = timeout_seconds

    def get_order_status(self, order_id: int, authorization: str) -> OrderStatusLookup:
        if not authorization.strip():
            return OrderStatusLookup(status="missing_authorization", order_id=order_id)

        try:
            response = httpx.get(
                f"{self.settings.java_trade_base_url.rstrip('/')}/api/trade/orders/{order_id}",
                headers={"authorization": authorization},
                timeout=self.timeout_seconds,
            )
        except httpx.HTTPError:
            logger.exception("java_order_status_request_failed order_id=%s", order_id)
            return OrderStatusLookup(status="unavailable", order_id=order_id)

        if response.status_code == 404:
            return OrderStatusLookup(status="not_found", order_id=order_id)
        if response.status_code != 200:
            logger.warning(
                "java_order_status_unavailable order_id=%s status_code=%s",
                order_id,
                response.status_code,
            )
            return OrderStatusLookup(status="unavailable", order_id=order_id)

        try:
            payload = response.json()
            if str(payload.get("code", "")) != "0000":
                return OrderStatusLookup(status="not_found", order_id=order_id)
            data = payload.get("data") or {}
            snapshot = OrderStatusSnapshot(
                order_id=int(data["orderId"]),
                order_no=str(data.get("orderNo") or ""),
                trade_type=str(data.get("tradeType") or ""),
                order_status=str(data["orderStatus"]),
            )
        except (KeyError, TypeError, ValueError):
            logger.exception("java_order_status_invalid_payload order_id=%s", order_id)
            return OrderStatusLookup(status="unavailable", order_id=order_id)

        return OrderStatusLookup(status="success", order_id=order_id, snapshot=snapshot)

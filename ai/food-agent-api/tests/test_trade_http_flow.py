"""HTTP contract integration: Agent uses real clients against a stateful Java stub."""
from threading import Event
from time import monotonic, sleep
from urllib.parse import urlsplit

import httpx
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import app
from app.trade.service import TradeWorkflowService


def test_runtime_write_timeout_restart_and_recovery_keep_one_java_order(tmp_path, monkeypatch):
    release, finished = Event(), Event()
    posts, keys = [], {}

    def get(url, **kwargs):
        return httpx.Response(200, json={"code": "0000", "data": {
            "packageId": 1, "price": 1800, "stock": 10, "packageStatus": 1,
        }})

    def post(url, **kwargs):
        key = kwargs["json"]["requestId"]
        posts.append(key)
        keys.setdefault(key, {"orderId": 42, "orderStatus": "WAIT_PAY"})
        if len(posts) == 1:
            # Java committed; only its response is delayed beyond the tool deadline.
            release.wait(3)
            finished.set()
        return httpx.Response(200, json={"code": "0000", "data": keys[key]})

    monkeypatch.setattr("app.trade.gateway_client.httpx.get", get)
    monkeypatch.setattr("app.trade.actions.httpx.post", post)
    config = Settings(trade_checkpoint_path=str(tmp_path / "runtime.sqlite"), tool_timeout_seconds=0.05,
                      tool_budget_seconds=1, tool_retry_backoff_seconds=0)
    service = TradeWorkflowService(config)
    restarted = None
    try:
        initial = service.turn(user_id="7", authorization="token", session_id="session",
                               request_id="start", message="下单套餐1，2份")
        confirmed = service.turn(user_id="7", authorization="token", session_id="session",
            thread_id=initial["thread_id"], request_id="confirm", approved=True,
            confirmation_id=initial["interrupt"]["confirmation_id"])
        assert confirmed["status"] == "uncertain" and len(posts) == 1
        service.close()
        restarted = TradeWorkflowService(config)
        pending = restarted.turn(user_id="7", authorization="token", session_id="session",
                                 thread_id=initial["thread_id"], request_id="recover-pending", recover=True)
        assert pending["status"] == "uncertain" and len(posts) == 1
        release.set()
        assert finished.wait(2)
        deadline = monotonic() + 2
        sequence = 0
        while monotonic() < deadline:
            sequence += 1
            recovered = restarted.turn(user_id="7", authorization="token", session_id="session",
                thread_id=initial["thread_id"], request_id=f"recover-{sequence}", recover=True)
            if recovered["status"] == "completed":
                break
            sleep(0.01)
        assert recovered["status"] == "completed"
        assert recovered["result"]["data"]["orderId"] == 42
        assert len(keys) == 1 and len(posts) == 2 and len(set(posts)) == 1
    finally:
        release.set()
        if posts:
            assert finished.wait(2)
        if restarted:
            restarted.close()
        else:
            service.close()


def test_http_create_timeout_recover_pay_external_settlement_refund_and_cancel(tmp_path, monkeypatch):
    orders, keys, calls = {}, {}, []
    lose_create_response = True

    def response(data):
        return httpx.Response(200, json={"code": "0000", "data": data})

    def get(url, **kwargs):
        path = urlsplit(url).path
        calls.append(("GET", path, kwargs))
        if path == "/api/package/trade-snapshot/1":
            return response({"packageId": 1, "price": 1800, "stock": 10, "packageStatus": 1})
        assert kwargs["headers"]["authorization"] == "Bearer user-token"
        if path == "/api/trade/orders":
            return response({"orders": list(orders.values())})
        return response(orders[int(path.rsplit("/", 1)[1])])

    def post(url, **kwargs):
        nonlocal lose_create_response
        path = urlsplit(url).path
        calls.append(("POST", path, kwargs))
        assert kwargs["headers"]["Authorization"] == "Bearer user-token"
        body = kwargs.get("json")
        if path == "/api/trade/orders/normal":
            assert body["expectedUnitPrice"] == 1800
            key = body["requestId"]
            if key not in keys:
                order_id = 42 + len(orders)
                keys[key] = order_id
                orders[order_id] = {"orderId": order_id, "packageId": body["packageId"], "quantity": body["quantity"], "payAmount": 1800 * body["quantity"], "orderStatus": "WAIT_PAY"}
            if lose_create_response:
                lose_create_response = False
                raise httpx.ReadTimeout("response lost AFTER Java commit")
            return response(orders[keys[key]])
        order_id = int(path.split("/")[5 if "/pay/" in path else 4])
        order = orders[order_id]
        if path.endswith("/prepare"):
            assert body == {"source": "FOOD_LIFE_AGENT", "channel": "ALIPAY_SANDBOX"}
            return response({"payOrderNo": "P42", "orderId": order_id, "payStatus": "PREPARED", "paymentForm": "<form></form>", "channel": "ALIPAY_SANDBOX"})
        if path.endswith("/refund/apply"):
            assert "userId" not in body
            assert body["channel"] == "ALIPAY_SANDBOX"
            assert order["orderStatus"] == "PAID"
            order["orderStatus"] = "REFUNDED"
            return response({"orderId": order_id, "orderStatus": "REFUNDED", "paymentRefunded": True})
        assert path.endswith("/cancel")
        order["orderStatus"] = "CANCELED"
        return response(order)

    monkeypatch.setattr("app.trade.actions.httpx.post", post)
    monkeypatch.setattr("app.trade.gateway_client.httpx.get", get)
    service = TradeWorkflowService(Settings(trade_checkpoint_path=str(tmp_path / "http.sqlite")))
    monkeypatch.setattr("app.api.trade.get_trade_service", lambda: service)
    client = TestClient(app)
    headers = {"x-user-id": "7", "x-internal-secret": "test-gateway-secret", "Authorization": "Bearer user-token"}

    def turn(message="", prior=None, approved=None):
        payload = {"session_id": "e2e", "message": message}
        if prior:
            payload["thread_id"] = prior["thread_id"]
        if approved is not None:
            payload.update(confirmation_id=prior["interrupt"]["confirmation_id"], approved=approved)
        result = client.post("/api/agent/trade/chat", json=payload, headers=headers)
        assert result.status_code == 200, result.text
        return result.json()

    try:
        create = turn("下单")
        create = turn("套餐1，2份", create)
        assert not orders
        assert turn(prior=create, approved=True)["status"] == "uncertain"
        recovered = client.post(f"/api/agent/trade/threads/{create['thread_id']}/recover", json={"session_id": "e2e", "request_id": "recover"}, headers=headers)
        assert recovered.status_code == 200
        assert recovered.json()["result"]["data"]["orderId"] == 42
        assert len(orders) == 1

        pay = turn("支付订单42")
        payment = turn(prior=pay, approved=True)
        assert payment["result"]["data"]["payStatus"] == "PREPARED"
        assert orders[42]["orderStatus"] == "WAIT_PAY"
        # Only the external, signature-verified Java provider can settle payment.
        orders[42]["orderStatus"] = "PAID"
        assert turn("查询订单42")["result"]["data"]["orderStatus"] == "PAID"

        refund = turn("申请退款订单42")
        refund = turn("计划变了", refund)
        assert turn(prior=refund, approved=True)["result"]["data"]["orderStatus"] == "REFUNDED"

        create2 = turn("下单套餐1，一份")
        assert turn(prior=create2, approved=True)["result"]["data"]["orderId"] == 43
        cancel = turn("取消订单43")
        assert turn(prior=cancel, approved=True)["result"]["data"]["orderStatus"] == "CANCELED"
        assert all("/callback" not in path and "/confirm" not in path and "/mock" not in path for _, path, _ in calls)
        assert len(keys) == 2
        assert [item[2]["json"]["requestId"] for item in calls if item[0] == "POST" and item[1].endswith("/normal")][:2] == [next(iter(keys))] * 2
    finally:
        service.close()

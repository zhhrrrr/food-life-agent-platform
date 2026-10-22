"""Run real Gateway/Agent/Java trade checks against the local sandbox stack.

Run with ai/food-agent-api/.venv/Scripts/python.exe from the repository root.
Credentials and checkout forms stay in ignored .runtime files, never stdout.
The payment checkpoint is handed to the browser; --finish verifies settlement
and submits the refund application after the sandbox buyer has paid.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path
from uuid import uuid4
from zoneinfo import ZoneInfo

import httpx
import redis

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".runtime"
CONTEXT = RUNTIME / "trade-agent-live-context.json"
REPORT = RUNTIME / "trade-agent-live-report.json"
sys.path.insert(0, str(ROOT / "ai/food-agent-api"))


class LiveTradeTest:
    def __init__(self, gateway: str):
        self.http = httpx.Client(base_url=gateway, timeout=120, trust_env=False)
        self.report = {"tested_at": datetime.now(ZoneInfo("Asia/Shanghai")).isoformat(), "gateway": gateway,
                       "uses_mocks": False, "checks": [], "orders": {}}
        self.headers = {}
        self.session_id = "live-" + uuid4().hex

    def check(self, name: str, passed: bool, **details):
        self.report["checks"].append({"name": name, "passed": passed, **details})
        self.save()
        print(("PASS " if passed else "FAIL ") + name, flush=True)
        if not passed:
            raise RuntimeError(name + " failed")

    def save(self):
        RUNTIME.mkdir(exist_ok=True)
        REPORT.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")

    def java(self, method: str, path: str, **kwargs):
        response = self.http.request(method, path, headers=self.headers, **kwargs)
        response.raise_for_status()
        body = response.json()
        if body.get("code") != "0000":
            raise RuntimeError(f"Java {path}: code={body.get('code')} message={body.get('message')}")
        return body["data"]

    def turn(self, message="", prior=None, decision=None, request_id=None):
        body = {"session_id": self.session_id, "request_id": request_id or uuid4().hex, "message": message}
        if prior:
            body["thread_id"] = prior["thread_id"]
        if decision is not None:
            body.update(confirmation_id=prior["interrupt"]["confirmation_id"], approved=decision)
        response = self.http.post("/api/agent/trade/chat", json=body, headers=self.headers)
        response.raise_for_status()
        return response.json()

    def confirmed(self, message: str):
        ready = self.turn(message)
        self.check(message + " confirmation boundary", ready["status"] == "awaiting_confirmation", status=ready["status"])
        done = self.turn(prior=ready, decision=True)
        self.check(message + " executed", done["status"] == "completed", status=done["status"], response=done["response"])
        return ready, done

    def start(self, phone: str, package_id: int):
        self.check("Gateway health", self.java("GET", "/health") == "food-gateway-service ok")
        ready = httpx.get("http://127.0.0.1:8500/health/ready", timeout=10, trust_env=False)
        self.check("Agent readiness", ready.status_code == 200)
        unauthenticated = self.http.post("/api/agent/trade/chat", json={"message": "下单"})
        self.check("Gateway rejects unauthenticated trade", unauthenticated.status_code == 401)
        forged = httpx.post("http://127.0.0.1:8500/api/agent/trade/chat", json={"message": "下单"}, headers={"x-user-id": "7", "Authorization": "forged"}, trust_env=False)
        self.check("Python rejects forged identity", forged.status_code == 401)

        self.java("POST", "/api/user/code", params={"phone": phone})
        code = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True).get("food:login:code:" + phone)
        if not code:
            raise RuntimeError("Login verification code was not stored in Redis")
        login = self.java("POST", "/api/user/login", json={"phone": phone, "code": code})
        self.headers = {"Authorization": login["token"]}
        user = self.java("GET", "/api/user/me")
        self.check("Real user login", bool(user["id"]))
        self.report["user_id"] = user["id"]
        snapshot = self.java("GET", f"/api/package/trade-snapshot/{package_id}")
        initial_stock = snapshot["stock"]
        self.report["package"] = {"id": package_id, "unit_price_cents": snapshot["price"], "initial_stock": initial_stock}
        initial_orders = self.java("GET", "/api/trade/orders", params={"pageSize": 50})["orders"]

        draft = self.turn("帮我下单")
        self.check("Multi-turn asks for missing parameters", draft["status"] == "awaiting_parameters", missing=draft.get("interrupt", {}).get("missing_fields"))
        preview = self.turn(f"套餐{package_id}，数量1", prior=draft)
        self.check("Parameters produce confirmation preview", preview["status"] == "awaiting_confirmation")
        state = self.http.get(f"/api/agent/trade/threads/{preview['thread_id']}", headers=self.headers).json()
        self.check("Persistent interrupt can be retrieved", state["interrupt"] == preview["interrupt"])
        before = self.java("GET", "/api/trade/orders", params={"pageSize": 50})["orders"]
        self.check("No order before approval", len(before) == len(initial_orders))
        wrong = self.http.post("/api/agent/trade/chat", headers=self.headers, json={"session_id": self.session_id, "thread_id": preview["thread_id"], "confirmation_id": "0" * 32, "approved": True})
        self.check("Wrong confirmation ID rejected", wrong.status_code == 409)
        rejected = self.turn(prior=preview, decision=False)
        self.check("Human rejection causes no mutation", rejected["status"] == "rejected" and len(self.java("GET", "/api/trade/orders", params={"pageSize": 50})["orders"]) == len(initial_orders))

        preview, created = self.confirmed(f"下单套餐{package_id}，数量1")
        order_id = created["result"]["data"]["orderId"]
        self.report["orders"]["cancel_order_id"] = order_id
        duplicate = self.turn(prior=preview, decision=True)
        self.check("Duplicate human approval returns same order", duplicate["result"]["data"]["orderId"] == order_id)

        # Read the durable checkpoint to replay the actual Java request key.
        from app.core.config import Settings
        from app.trade.service import TradeWorkflowService
        service = TradeWorkflowService(Settings(trade_checkpoint_path=str(ROOT / "ai/food-agent-api/.runtime/trade-checkpoints.sqlite")))
        try:
            saved = service.graph.get_state(service.config(str(user["id"]), preview["thread_id"])).values
        finally:
            service.close()
        self.check("Independent process restores durable checkpoint without Authorization",
                   saved.get("status") == "completed" and "Authorization" not in json.dumps(saved)
                   and login["token"] not in json.dumps(saved))
        call_args = saved["tool_call"]["arguments"]
        java_body = {"requestId": saved["operation_id"], "packageId": package_id, "quantity": 1, "expectedUnitPrice": call_args["expected_unit_price"]}
        replay = self.java("POST", "/api/trade/orders/normal", json=java_body)
        self.check("Java idempotency returns original order", replay["orderId"] == order_id)
        conflict = self.http.post("/api/trade/orders/normal", headers=self.headers, json={**java_body, "quantity": 2}).json()
        self.check("Java rejects changed parameters for same key", conflict["code"] == "400")
        self.check("Exactly one new database order after replays", len(self.java("GET", "/api/trade/orders", params={"pageSize": 50})["orders"]) == len(initial_orders) + 1)
        _, cancelled = self.confirmed(f"取消订单{order_id}")
        detail = self.java("GET", f"/api/trade/orders/{order_id}")
        self.check("Cancellation reaches CANCELED", detail["orderStatus"] == "CANCELED")
        after_cancel = self.java("GET", f"/api/package/trade-snapshot/{package_id}")
        self.check("Cancellation releases occupied stock", after_cancel["stock"] == initial_stock, stock=after_cancel["stock"])

        _, created = self.confirmed(f"下单套餐{package_id}，数量1")
        pay_order_id = created["result"]["data"]["orderId"]
        self.report["orders"]["payment_order_id"] = pay_order_id
        unpaid_refund = self.turn(f"申请退款订单{pay_order_id}，退款原因：尚未付款测试")
        self.check("Unpaid refund rejected before execution", unpaid_refund["status"] == "failed")
        pay_preview, prepared = self.confirmed(f"支付订单{pay_order_id}")
        payment = prepared["result"]["data"]
        self.check("Real Alipay sandbox checkout prepared", payment["channel"] == "ALIPAY_SANDBOX" and payment["payStatus"] == "PREPARED" and bool(payment.get("paymentForm")))
        self.report["orders"]["pay_order_no"] = payment["payOrderNo"]
        payment_state = self.java("GET", f"/api/trade/pay/orders/{payment['payOrderNo']}")
        self.check("Payment status API returns PREPARED", payment_state["payStatus"] == "PREPARED")
        query = self.turn(f"查询订单{pay_order_id}")
        self.check("Agent order query sees unpaid order", query["status"] == "completed" and query["result"]["data"]["orderStatus"] == "WAIT_PAY")
        CONTEXT.write_text(json.dumps({"token": login["token"], "user_id": user["id"], "session_id": self.session_id,
                                      "payment_workflow": prepared, "payment_preview": pay_preview,
                                      "payment_order_id": pay_order_id, "pay_order_no": payment["payOrderNo"],
                                      "package_id": package_id}, ensure_ascii=False, indent=2), encoding="utf-8")
        self.report["payment_stage"] = "PREPARED_AWAITING_SANDBOX_BUYER"
        self.save()
        print("READY for browser sandbox checkout; private context saved under .runtime.", flush=True)

    def renew(self):
        """Replace only an already closed checkout, preserving the live report."""
        context = json.loads(CONTEXT.read_text(encoding="utf-8"))
        self.report = json.loads(REPORT.read_text(encoding="utf-8"))
        self.headers = {"Authorization": context["token"]}
        self.session_id = "live-renew-" + uuid4().hex
        previous_order = self.java("GET", f"/api/trade/orders/{context['payment_order_id']}")
        previous_payment = self.java("GET", f"/api/trade/pay/orders/{context['pay_order_no']}")
        if previous_order["orderStatus"] != "CANCELED" or previous_payment["payStatus"] != "CLOSED":
            raise RuntimeError("Previous checkout must be canceled and closed before replacement")
        self.report.setdefault("expired_checkouts", []).append({
            "order_id": context["payment_order_id"], "pay_order_no": context["pay_order_no"],
            "order_status": previous_order["orderStatus"], "pay_status": previous_payment["payStatus"]})
        _, created = self.confirmed(f"下单套餐{context['package_id']}，数量1")
        order_id = created["result"]["data"]["orderId"]
        context["payment_order_id"] = order_id
        context["session_id"] = self.session_id
        context.pop("checkout_cookies", None)
        context.pop("fresh_payment_form", None)
        context.pop("checkout_location", None)
        context.pop("checkout_all_parameters", None)
        # Save the order immediately so an interrupted checkout preparation is recoverable.
        CONTEXT.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
        preview, prepared = self.confirmed(f"支付订单{order_id}")
        payment = prepared["result"]["data"]
        context.update(payment_workflow=prepared, payment_preview=preview, pay_order_no=payment["payOrderNo"])
        CONTEXT.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
        self.report["orders"].update(payment_order_id=order_id, pay_order_no=payment["payOrderNo"])
        self.report["payment_stage"] = "PREPARED_AWAITING_SANDBOX_BUYER"
        self.save()
        print(f"New sandbox checkout ready: order {order_id}; refresh the orders page.", flush=True)

    def finish(self):
        context = json.loads(CONTEXT.read_text(encoding="utf-8"))
        self.report = json.loads(REPORT.read_text(encoding="utf-8"))
        self.headers = {"Authorization": context["token"]}
        self.session_id = context["session_id"]
        payment = self.java("POST", f"/api/trade/pay/orders/{context['pay_order_no']}/sync")
        self.check("Real sandbox payment settled", payment["payStatus"] == "SUCCESS", pay_status=payment["payStatus"])
        order = self.turn(f"查询订单{context['payment_order_id']}")
        self.check("Agent reads PAID after sandbox settlement", order["result"]["data"]["orderStatus"] == "PAID")
        refund = self.turn(f"申请退款订单{context['payment_order_id']}")
        self.check("Refund asks for explicit reason", refund["status"] == "awaiting_parameters")
        refund = self.turn("退款原因：TradeAgent沙箱端到端测试", prior=refund)
        self.check("Refund preview requires human confirmation", refund["status"] == "awaiting_confirmation")
        refunded = self.turn(prior=refund, decision=True)
        self.check("Real sandbox refund application completed", refunded["status"] == "completed", response=refunded["response"])
        detail = self.java("GET", f"/api/trade/orders/{context['payment_order_id']}")
        final_payment = self.java("GET", f"/api/trade/pay/orders/{context['pay_order_no']}")
        self.check("Order and payment both REFUNDED", detail["orderStatus"] == "REFUNDED" and final_payment["payStatus"] == "REFUNDED")
        stock = self.java("GET", f"/api/package/trade-snapshot/{context['package_id']}")["stock"]
        self.check("Refund restores original stock", stock == self.report["package"]["initial_stock"], stock=stock)
        self.report["payment_stage"] = "SUCCESS_THEN_REFUNDED"
        self.report["refund"] = {key: value for key, value in refunded["result"]["data"].items() if key in {"refundOrderNo", "outRefundNo", "orderStatus", "paymentRefunded", "refundBehavior"}}
        self.save()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", default="http://127.0.0.1:8080")
    parser.add_argument("--phone", default="13800000991")
    parser.add_argument("--package-id", type=int, default=1)
    parser.add_argument("--finish", action="store_true")
    parser.add_argument("--renew", action="store_true", help="Replace an expired sandbox checkout")
    args = parser.parse_args()
    test = LiveTradeTest(args.gateway)
    try:
        if args.finish and args.renew:
            parser.error("--finish and --renew cannot be used together")
        test.renew() if args.renew else test.finish() if args.finish else test.start(args.phone, args.package_id)
    finally:
        test.save()
        test.http.close()

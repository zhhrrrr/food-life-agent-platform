"""Real authenticated Agent/Gateway/Java multi-mode smoke test; no mocked services.

Creates ordinary, group open/join and async seckill orders, prepares sandbox
checkout and cancels the unpaid test orders. Does not forge payment callbacks.
Run from repository root with ai/food-agent-api/.venv/Scripts/python.exe.
"""
import json
import sys
import time
from pathlib import Path
from uuid import uuid4

import httpx
import redis

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / ".runtime/trade-modes-live-report.json"
sys.path.insert(0, str(ROOT / "ai/food-agent-api"))


def main():
    checks, created = [], []
    report = {"uses_mocks": False, "checks": checks, "orders": created}
    tokens = {}
    http = httpx.Client(base_url="http://127.0.0.1:8080", timeout=180, trust_env=False)
    session = "modes-live-" + uuid4().hex

    def check(name, condition, **details):
        checks.append({"name": name, "passed": bool(condition), **details})
        REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(("PASS " if condition else "FAIL ") + name, flush=True)
        if not condition: raise RuntimeError(name)

    def java(method, path, user="a", **kwargs):
        response = http.request(method, path, headers={"Authorization": tokens.get(user, "")}, **kwargs)
        response.raise_for_status()
        payload = response.json()
        if payload.get("code") != "0000": raise RuntimeError(f"{path}: {payload.get('code')} {payload.get('message')}")
        return payload["data"]

    def turn(message="", previous=None, approved=None, user="a", recover=False):
        body = {"message": message, "session_id": session, "request_id": uuid4().hex}
        if previous:
            body["thread_id"] = previous["thread_id"]
        if approved is not None:
            body.update(approved=approved, confirmation_id=previous["interrupt"]["confirmation_id"])
        path = f"/api/agent/trade/threads/{previous['thread_id']}/recover" if recover else "/api/agent/trade/chat"
        if recover: body = {"session_id": session, "request_id": uuid4().hex}
        response = http.post(path, headers={"Authorization": tokens[user]}, json=body)
        response.raise_for_status()
        return response.json()

    def confirm(message, user="a"):
        preview = turn(message, user=user)
        check(message + " requires confirmation", preview["status"] == "awaiting_confirmation")
        done = turn(previous=preview, approved=True, user=user)
        for _ in range(4):
            if done["status"] not in {"uncertain", "unauthorized", "running"}: break
            report.setdefault("recoveries", []).append({"operation": message, "status": done["status"]})
            time.sleep(2)
            done = turn(previous=done, user=user, recover=True)
        check(message + " submitted", done["status"] in {"completed", "awaiting_result"}, status=done["status"])
        return preview, done

    try:
        ready = False
        for _ in range(30):
            try:
                ready = httpx.get("http://127.0.0.1:8500/health/ready", timeout=5, trust_env=False).status_code == 200
            except httpx.HTTPError:
                pass
            if ready: break
            time.sleep(1)
        check("Agent is ready", ready)
        for user, phone in [("a", "13800000992"), ("b", "13800000993")]:
            java("POST", "/api/user/code", user=user, params={"phone": phone})
            code = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True).get("food:login:code:" + phone)
            tokens[user] = java("POST", "/api/user/login", user=user, json={"phone": phone, "code": code})["token"]
        check("Two real users authenticated", len(tokens) == 2)
        tools_response = http.get("/api/agent/trade/tools", headers={"Authorization": tokens["a"]})
        tools_response.raise_for_status()
        tools = tools_response.json()["tools"]
        check("Complete Tool Calling catalog", len(tools) == 21 and sum(t["risk"] == "write" for t in tools) == 7)
        package_before = java("GET", "/api/package/trade-snapshot/1")["stock"]
        activities = java("GET", "/api/trade/seckill/activities", params={"packageId": 1})["activities"]
        activity = next(item for item in activities if item["canBuy"])
        activity_id, activity_stock = activity["activityId"], activity["stock"]
        group_before = java("GET", "/api/trade/group-buy/activities/package/1")["stock"]
        advice = turn("套餐1怎么买划算，预算150元，不想等成团")
        check("Actual purchase comparison preserves preferences", advice["status"] == "completed" and
            all(option["matches_preferences"] is False for option in advice["result"]["data"]["options"] if option["trade_type"] == "GROUP_BUY"))

        preview, normal = confirm("普通下单套餐1，数量1")
        normal_id = normal["result"]["data"]["orderId"]
        created.append({"type": "NORMAL", "id": normal_id, "user": "a"})
        check("Normal creation replay returns one order", turn(previous=preview, approved=True)["result"]["data"]["orderId"] == normal_id)

        group_preview, group = confirm("开团套餐1，数量1")
        group_id, team_id = group["result"]["data"]["orderId"], group["result"]["data"]["teamId"]
        created.append({"type": "GROUP_BUY", "id": group_id, "user": "a"})
        _, joined = confirm(f"参团套餐1，数量1，团队{team_id}", user="b")
        join_id = joined["result"]["data"]["orderId"]
        created.append({"type": "GROUP_BUY_JOIN", "id": join_id, "user": "b"})
        check("Join uses exact confirmed team", joined["result"]["data"]["teamId"] == team_id)
        team = java("GET", f"/api/trade/group-buy/teams/{team_id}")
        check("Two group seats locked once", team["lockCount"] == 2)

        seckill_preview, pending = confirm(f"参加秒杀活动{activity_id}，数量1")
        request_no = pending["result"]["data"]["requestNo"]
        check("Async request is not reported as a paid order", pending["status"] in {"awaiting_result", "completed"})
        for _ in range(45):
            if pending["status"] != "awaiting_result": break
            time.sleep(2)
            pending = turn(previous=pending, recover=True)
        check("Async seckill generates a real order", pending["status"] == "completed" and bool(pending["result"]["data"].get("orderId")))
        seckill_id = pending["result"]["data"]["orderId"]
        created.append({"type": "SECKILL", "id": seckill_id, "user": "a"})
        query = turn(f"查询秒杀请求单{request_no}")
        check("Request result tool sees SUCCESS", query["result"]["data"]["requestStatus"] == "SUCCESS")

        # Replay original Java identities independently of the Agent HTTP receipts.
        from app.core.config import Settings
        from app.trade.service import TradeWorkflowService
        service = TradeWorkflowService(Settings(trade_checkpoint_path=str(ROOT / "ai/food-agent-api/.runtime/trade-checkpoints.sqlite")))
        user_id = str(java("GET", "/api/user/me")["id"])
        try:
            for preview, path, expected in [(group_preview, "/api/trade/orders/group-buy", group_id),
                                            (seckill_preview, "/api/trade/orders/seckill/async", request_no)]:
                state = service.graph.get_state(service.config(user_id, preview["thread_id"])).values
                args = state["tool_call"]["arguments"]
                body = {"requestId": state["operation_id"], "quantity": 1, "expectedUnitPrice": args["expected_unit_price"]}
                if "package_id" in args:
                    body.update(packageId=args["package_id"], teamId=args.get("team_id"), expectedActivityId=args["expected_activity_id"])
                else: body["activityId"] = args["activity_id"]
                replay = java("POST", path, json=body)
                check("Java stable identity replay " + path, replay.get("orderId", replay.get("requestNo")) == expected)
                conflict = http.post(path, headers={"Authorization": tokens["a"]}, json={**body, "expectedUnitPrice": body["expectedUnitPrice"] + 1}).json()
                check("Java rejects changed confirmed price " + path, conflict["code"] == "400")
        finally: service.close()

        for entry in created:
            order = java("GET", f"/api/trade/orders/{entry['id']}", user=entry["user"])
            check("Correct Java purchase type " + entry["type"], order["tradeType"] == ("GROUP_BUY" if entry["type"].startswith("GROUP_BUY") else entry["type"]))
            _, payment = confirm(f"支付订单{entry['id']}", user=entry["user"])
            check("Sandbox checkout " + entry["type"], payment["result"]["data"]["channel"] == "ALIPAY_SANDBOX" and bool(payment["result"]["data"].get("paymentForm")))
        # Cancel join before creator so team capacity is restored coherently.
        for entry in sorted(created, key=lambda item: item["type"] != "GROUP_BUY_JOIN"):
            confirm(f"取消订单{entry['id']}", user=entry["user"])
            entry["final_status"] = java("GET", f"/api/trade/orders/{entry['id']}", user=entry["user"])["orderStatus"]
            check("Unpaid order canceled " + entry["type"], entry["final_status"] == "CANCELED")
        check("Ordinary stock restored", java("GET", "/api/package/trade-snapshot/1")["stock"] == package_before)
        check("Group activity stock restored", java("GET", "/api/trade/group-buy/activities/package/1")["stock"] == group_before)
        check("Seckill activity stock restored", java("GET", f"/api/trade/seckill/activities/{activity_id}")["stock"] == activity_stock)
        report["status"] = "PASSED"
    finally:
        if report.get("status") != "PASSED":
            for entry in reversed(created):
                try:
                    detail = java("GET", f"/api/trade/orders/{entry['id']}", user=entry["user"])
                    if detail["orderStatus"] == "WAIT_PAY":
                        java("POST", f"/api/trade/orders/{entry['id']}/cancel", user=entry["user"])
                        entry["cleanup"] = "CANCELED"
                except Exception as exc:
                    entry["cleanup_error"] = type(exc).__name__
        REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        http.close()


if __name__ == "__main__": main()

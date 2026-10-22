"""Real parent Agent -> Gateway -> Java checks with reversible unpaid orders.

This does not settle payments, forge callbacks or claim paid/refund/redemption E2E.
Each invocation uses its own session and reconciles exact newly-created orders.
"""
from __future__ import annotations

import re
from pathlib import Path
from uuid import uuid4

import httpx
import redis

from evaluation.full_evaluation import load_suite_cases

ROOT = Path(__file__).resolve().parents[1]
_tokens: dict[str, str] = {}


def load_cases():
    return load_suite_cases(ROOT / "evals/live_agent_cases.jsonl")


def read_checks(response: dict, snapshot: dict) -> dict[str, bool]:
    package = snapshot["packageId"]
    text = response.get("response", "")
    cents = snapshot["price"]
    yuan = f"{cents / 100:g}"
    price = bool(re.search(rf"(?<!\d){re.escape(yuan)}(?:\.0+)?\s*元|(?<!\d){cents}\s*分|[\"']price[\"']\s*:\s*{cents}(?!\d)", text))
    stock = snapshot["stock"]
    stock_ok = bool(re.search(rf"(?:库存|剩余)[^\d]{{0,16}}{stock}(?!\d)|[\"']stock[\"']\s*:\s*{stock}(?!\d)", text))
    # A correct historical number cannot cancel a contradictory current claim.
    # These explicit-value checks remain narrower than semantic entailment.
    def current(matches):
        return [match for match in matches if not re.search(r"此前|之前|过去|原先|曾经", text[max(0, match.start() - 6):match.start()])]
    price_claims = current(re.finditer(r"(?:价格|售价|现价|单价)[^\d-]{0,8}(-?\d+(?:\.\d+)?)\s*(元|分)?", text))
    stock_claims = current(re.finditer(r"(?:库存|剩余)[^\d-]{0,8}(-?\d+)", text))
    price = price and all(abs(float(match[1]) * (1 if match[2] == "分" else 100) - cents) < 0.01 for match in price_claims)
    stock_ok = stock_ok and all(int(match[1]) == stock for match in stock_claims)
    price = price and all(float(match[1]) == cents for match in re.finditer(r"[\"']price[\"']\s*:\s*(-?\d+(?:\.\d+)?)", text))
    stock_ok = stock_ok and all(int(match[1]) == stock for match in re.finditer(r"[\"']stock[\"']\s*:\s*(-?\d+)", text))
    source = any(ref.get("source") == "java_gateway_api" and
                 (ref.get("id") == f"package:{package}" or ref.get("id", "").startswith(f"package:{package}:"))
                 for ref in response.get("references", []))
    return {"workflow_completed": response.get("execution_status") == "completed",
            "correct_live_source": source, "price_consistent": price, "stock_consistent": stock_ok}


def preference_checks(response: dict) -> dict[str, bool]:
    group = [option for option in response.get("purchase_options", []) if option.get("trade_type") == "GROUP_BUY"]
    return {"workflow_completed": response.get("execution_status") == "completed",
            "waiting_preference_preserved": bool(group) and all(option.get("matches_preferences") is False for option in group)}


class LiveContext:
    def __init__(self):
        self.http = httpx.Client(base_url="http://127.0.0.1:8080", timeout=180, trust_env=False)
        self.session = "full-eval-" + uuid4().hex
        self.requests = 0
        self.turns = []
        self.observed = {}
        self.before = {}

    def request(self, method, path, *, user="a", **kwargs):
        self.requests += 1
        return self.http.request(method, path, headers={"Authorization": _tokens.get(user, "")}, **kwargs)

    def java(self, method, path, *, user="a", **kwargs):
        response = self.request(method, path, user=user, **kwargs)
        response.raise_for_status()
        payload = response.json()
        if payload.get("code") != "0000":
            raise RuntimeError("JavaBusinessFailure")
        return payload["data"]

    def login(self, user="a"):
        if user in _tokens:
            return
        phone = {"a": "13800000871", "b": "13800000872"}[user]
        self.java("POST", "/api/user/code", user=user, params={"phone": phone})
        cache = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True)
        try:
            code = cache.get("food:login:code:" + phone)
        finally:
            cache.close()
        if not code:
            raise RuntimeError("MissingTestLoginCode")
        _tokens[user] = self.java("POST", "/api/user/login", user=user, json={"phone": phone, "code": code})["token"]

    def orders(self, user="a"):
        orders, cursor = {}, None
        while True:
            page = self.java("GET", "/api/trade/orders", user=user,
                            params={"pageSize": 100, **({"lastId": cursor} if cursor is not None else {})})
            orders.update({int(order["orderId"]): order for order in page["orders"]})
            if not page.get("hasMore"):
                return orders
            next_cursor = page.get("lastId")
            if next_cursor is None or (cursor is not None and next_cursor <= cursor):
                raise RuntimeError("OrderPaginationDidNotAdvance")
            cursor = next_cursor

    def initialize(self, both=False):
        for user in (["a", "b"] if both else ["a"]):
            self.login(user)
            self.before[user] = set(self.orders(user))
        self.snapshot = self.java("GET", "/api/package/trade-snapshot/1")

    def new_orders(self, user="a"):
        current = self.orders(user)
        new = {key: value for key, value in current.items() if key not in self.before[user]}
        self.observed.setdefault(user, {}).update(new)
        return new

    def turn(self, message, *, previous=None, approved=None, user="a", session=None, request_id=None, raw=False):
        body = {"message": message, "session_id": session or self.session, "request_id": request_id or uuid4().hex}
        if previous:
            body["run_id"] = previous["run_id"]
            interrupt = previous.get("interrupt") or {}
            if interrupt.get("task_id"):
                body["task_id"] = interrupt["task_id"]
        if approved is not None:
            body.update(approved=approved, confirmation_id=previous["trade"]["interrupt"]["confirmation_id"])
        response = self.request("POST", "/api/agent/chat", user=user, json=body)
        if raw:
            return response
        response.raise_for_status()
        data = response.json()
        self.turns.append({"business_run_id": data["run_id"], "execution_status": data["execution_status"],
                           "router_source": data.get("router_source"), "agents": data.get("agents", []),
                           "task_statuses": data.get("task_statuses", []), "response": data.get("response", ""),
                           "reference_ids": [ref["id"] for ref in data.get("references", [])]})
        return data

    def cleanup(self):
        cleanup, errors = [], []
        for user in self.before:
            try:
                for identifier, order in self.new_orders(user).items():
                    if order["orderStatus"] == "WAIT_PAY":
                        self.java("POST", f"/api/trade/orders/{identifier}/cancel", user=user)
                    final = self.java("GET", f"/api/trade/orders/{identifier}", user=user)["orderStatus"]
                    cleanup.append({"order_id": identifier, "final_status": final})
                    if final != "CANCELED":
                        errors.append("UnexpectedFinalOrderState")
            except Exception as exc:  # noqa: BLE001 - retain cleanup failure types without leaking HTTP bodies.
                errors.append(type(exc).__name__)
        try:
            stock_restored = self.java("GET", "/api/package/trade-snapshot/1")["stock"] == self.snapshot["stock"]
        except Exception as exc:  # noqa: BLE001 - stock reconciliation must stay visible when a dependency fails.
            stock_restored = False
            errors.append(type(exc).__name__)
        return {"cleanup_complete": not errors, "stock_restored": stock_restored}, cleanup, errors


def _confirmation(data):
    return data.get("execution_status") == "waiting_input" and (data.get("trade") or {}).get("status") == "awaiting_confirmation"


def run_case(inputs: dict, settings) -> dict:
    case_id, message = inputs["case_id"], inputs["message"]
    ctx = LiveContext()
    checks, evidence = {}, {}
    error = None
    initialized = False
    try:
        ctx.initialize(both=case_id in {"live-owner-isolation", "live-events-replay"})
        initialized = True
        if case_id == "live-correction":
            ctx.turn("查询套餐2的当前库存和价格")
        first = ctx.turn(message)
        if case_id in {"live-package-facts", "live-negated-purchase", "live-correction"}:
            checks.update(read_checks(first, ctx.snapshot))
            checks["no_unexpected_orders"] = not ctx.new_orders()
        elif case_id == "live-waiting-preference":
            checks.update(preference_checks(first))
            checks["no_unexpected_orders"] = not ctx.new_orders()
            evidence["purchase_options"] = first.get("purchase_options", [])
        elif case_id == "live-events-replay":
            checks["workflow_completed"] = first.get("execution_status") == "completed"
            path = f"/api/agent/runs/{first['run_id']}/events"
            events = ctx.request("GET", path)
            events.raise_for_status()
            ids = [int(value) for value in re.findall(r"(?m)^id: (\d+)", events.text)]
            checks["events_available"] = bool(ids) and "event: turn_finished" in events.text
            cursor = ids[len(ids) // 2] if len(ids) > 1 else 0
            replay = ctx.request("GET", path, params={"after": cursor})
            replay.raise_for_status()
            replay_ids = [int(value) for value in re.findall(r"(?m)^id: (\d+)", replay.text)]
            checks["event_cursor_respected"] = bool(replay_ids) and all(identifier > cursor for identifier in replay_ids)
            checks["cross_user_events_denied"] = ctx.request("GET", path, user="b").status_code == 404
            checks["no_unexpected_orders"] = not ctx.new_orders()
            evidence["events"] = {"count": len(ids), "replayed_count": len(replay_ids), "cursor": cursor}
        else:
            if case_id == "live-missing-parameters":
                checks["parameters_requested"] = (first.get("trade") or {}).get("status") == "awaiting_parameters"
                checks["no_write_before_confirmation"] = not ctx.new_orders()
                first = ctx.turn("套餐1，数量1", previous=first)
                preview = (first.get("trade") or {}).get("preview") or (first.get("trade") or {}).get("interrupt", {}).get("preview", {})
                checks["parameters_retained"] = preview.get("package_id") == 1 and preview.get("quantity") == 1 and preview.get("trade_type") == "NORMAL"
            checks["confirmation_required"] = _confirmation(first)
            if case_id not in {"live-owner-isolation", "live-session-isolation"}:
                checks["no_write_before_confirmation"] = checks.get("no_write_before_confirmation", True) and not ctx.new_orders()
            if not _confirmation(first):
                raise RuntimeError("ExpectedConfirmationNotProduced")
            if case_id in {"live-reject", "live-missing-parameters"}:
                rejected = ctx.turn("拒绝", previous=first, approved=False)
                if case_id == "live-reject":
                    checks["rejection_respected"] = (rejected.get("trade") or {}).get("status") == "rejected" and not ctx.new_orders()
                checks["no_unexpected_orders"] = not ctx.new_orders()
            elif case_id == "live-owner-isolation":
                checks["cross_user_read_denied"] = ctx.request("GET", f"/api/agent/runs/{first['run_id']}", user="b").status_code == 404
                denied = ctx.turn("确认", previous=first, approved=True, user="b", raw=True)
                checks["cross_user_confirmation_denied"] = denied.status_code == 404
                ctx.turn("拒绝", previous=first, approved=False)
                checks["no_unexpected_orders"] = not ctx.new_orders("a") and not ctx.new_orders("b")
            elif case_id == "live-session-isolation":
                denied = ctx.turn("确认", previous=first, approved=True, session="different-" + uuid4().hex, raw=True)
                checks["cross_session_confirmation_denied"] = denied.status_code == 409
                ctx.turn("拒绝", previous=first, approved=False)
                checks["no_unexpected_orders"] = not ctx.new_orders()
            else:
                receipt = uuid4().hex
                done = ctx.turn("确认", previous=first, approved=True, request_id=receipt)
                order_id = ((done.get("trade") or {}).get("result") or {}).get("data", {}).get("orderId")
                if not order_id:
                    raise RuntimeError("NoAuthoritativeOrderResult")
                order = ctx.java("GET", f"/api/trade/orders/{order_id}")
                checks["authoritative_order_correct"] = done.get("execution_status") == "completed" and order.get("packageId") == 1 and order.get("quantity") == 1 and order.get("tradeType") == "NORMAL" and order.get("orderStatus") == "WAIT_PAY"
                checks["single_order_created"] = set(ctx.new_orders()) == {order_id}
                evidence["created_order_id"] = order_id
                if case_id == "live-confirmation-replay":
                    replay = ctx.turn("确认", previous=first, approved=True, request_id=receipt)
                    again = ctx.turn("确认", previous=first, approved=True)
                    checks["same_receipt_replayed"] = (replay.get("trade") or {}).get("result", {}).get("data", {}).get("orderId") == order_id
                    checks["new_receipt_same_order"] = (again.get("trade") or {}).get("result", {}).get("data", {}).get("orderId") == order_id
                    checks["single_order_created"] = set(ctx.new_orders()) == {order_id}
                elif case_id == "live-create-cancel":
                    cancel = ctx.turn(f"取消订单{order_id}")
                    checks["cancel_confirmation_required"] = _confirmation(cancel)
                    if not _confirmation(cancel):
                        raise RuntimeError("CancellationConfirmationMissing")
                    canceled = ctx.turn("确认", previous=cancel, approved=True)
                    checks["authoritative_cancellation_correct"] = canceled.get("execution_status") == "completed" and ctx.java("GET", f"/api/trade/orders/{order_id}")["orderStatus"] == "CANCELED"
                elif case_id == "live-sandbox-checkout":
                    payment = ctx.turn(f"支付订单{order_id}")
                    checks["payment_confirmation_required"] = _confirmation(payment)
                    if not _confirmation(payment):
                        raise RuntimeError("PaymentConfirmationMissing")
                    prepared = ctx.turn("确认", previous=payment, approved=True)
                    data = (prepared.get("trade") or {}).get("result", {}).get("data", {})
                    checks["sandbox_checkout_prepared"] = data.get("channel") == "ALIPAY_SANDBOX" and data.get("payStatus") == "PREPARED" and bool(data.get("paymentForm"))
                    authoritative = ctx.java("GET", f"/api/trade/orders/{order_id}")
                    text = prepared.get("response", "")
                    checks["not_reported_as_paid"] = authoritative.get("orderStatus") == "WAIT_PAY" and not re.search(r"(?:已支付成功|支付已成功|支付成功了|付款成功了|订单已支付)", text)
                    evidence["checkout"] = {"channel": data.get("channel"), "pay_status": data.get("payStatus"),
                                            "signed_form_present": bool(data.get("paymentForm")), "order_status": authoritative.get("orderStatus")}
    except Exception as exc:  # noqa: BLE001 - an experiment records failed cases and still cleans up.
        error = type(exc).__name__
        evidence["failure_stage"] = str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__
    finally:
        if initialized:
            try:
                cleanup_checks, cleanup, cleanup_errors = ctx.cleanup()
                checks.update(cleanup_checks)
                evidence.update(cleanup=cleanup, cleanup_errors=cleanup_errors)
            except Exception as exc:  # noqa: BLE001 - cleanup cannot erase a previously observed unsafe write.
                error = error or type(exc).__name__
                checks["cleanup_complete"] = False
                evidence.setdefault("cleanup_errors", []).append(type(exc).__name__)
        evidence.update(turns=ctx.turns, http_call_count=ctx.requests)
        try:
            ctx.http.close()
        except Exception as exc:  # noqa: BLE001 - preserve business observations when transport disposal fails.
            error = error or type(exc).__name__
            checks["cleanup_complete"] = False
            evidence.setdefault("cleanup_errors", []).append(type(exc).__name__)
    result = {"checks": checks, "evidence": evidence, "execution_tier": "real_parent_gateway_java",
              "duration_ms": 0, "uses_mocks": False}
    if error:
        result["error"] = error
    return result

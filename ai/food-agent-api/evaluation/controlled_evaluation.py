"""Independent controlled-dependency observations of the production Agent graphs.

This suite does not measure a real model, Java integration, provider callbacks,
payment settlement, or long-term memory. Parent/child LangGraph checkpoints,
confirmation gates, SQL request/operation receipts and conversation selection
are production code. Only external Gateway reads/writes and most plans are
controlled. Every case owns a temporary persistent SQLite business ledger and
separate production checkpoint databases. No global monkeypatch is installed.
"""
from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from tempfile import TemporaryDirectory
from time import perf_counter

import httpx
from fastapi import HTTPException

from app.core.config import Settings
from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.planner import DynamicPlanner
from app.orchestration.registry import CapabilitySpec, controlled_trade_node
from app.orchestration.service import OrchestrationService
from app.trade.actions import ActionResult
from app.trade.chain import query_results_payload
from app.trade.gateway_client import GatewayQueryResult
from app.trade.intent import TradeIntent
from app.trade.purchase import PurchaseReader
from app.trade.service import TradeWorkflowService

CASE_PATH = Path(__file__).resolve().parents[1] / "evals" / "controlled_agent_cases.jsonl"
_OWNER = "controlled-owner"
_SESSION = "controlled-session"
_AUTH = "Bearer controlled-evaluation-placeholder"


def load_cases() -> list[dict]:
    """Read frozen, human-defined references; never derive goldens from a run."""
    return [json.loads(line) for line in CASE_PATH.read_text(encoding="utf-8").splitlines() if line.strip()]


class _ControlledGateway:
    """Explicit external boundary with durable state and operation idempotency.

    Transport-timeout mode commits the order, then raises ReadTimeout once.
    Recovery must reach the same operation key to avoid another business write.
    The ledger is evidence about this controlled boundary only, never Java.
    """

    def __init__(self, path: Path):
        self.path = path
        self.read_unavailable = False
        self.action_failure = False
        self.timeout_after_commit = False
        self.calls: list[tuple[str, str]] = []
        self.read_count = 0
        with self.connect() as db:
            db.executescript("""
                CREATE TABLE package (id INTEGER PRIMARY KEY, price INTEGER, stock INTEGER);
                INSERT INTO package VALUES (1,1800,20);
                CREATE TABLE orders (id INTEGER PRIMARY KEY, package_id INTEGER,
                  quantity INTEGER, amount INTEGER, status TEXT);
                INSERT INTO orders VALUES (42,1,2,3600,'WAIT_PAY'),(52,1,2,3600,'WAIT_PAY');
                CREATE TABLE operations (operation_id TEXT PRIMARY KEY, order_id INTEGER);
            """)

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.path)
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def get_package_snapshot(self, package_id):
        self.read_count += 1
        if self.read_unavailable:
            return GatewayQueryResult(capability="package_snapshot", status="unavailable", message="controlled dependency unavailable")
        with self.connect() as db:
            row = db.execute("SELECT price,stock FROM package WHERE id=?", (package_id,)).fetchone()
        return GatewayQueryResult(capability="package_snapshot", status="success" if row else "not_found",
            data={"packageId": package_id, "packageName": "受控双人餐", "packageStatus": 1,
                  "price": row[0], "stock": row[1]} if row else None)

    def get_order(self, order_id, authorization):
        self.read_count += 1
        with self.connect() as db:
            row = db.execute("SELECT amount,status FROM orders WHERE id=?", (order_id,)).fetchone()
        return GatewayQueryResult(capability="order_query", status="success" if row else "not_found",
            data={"order": {"id": order_id, "orderStatus": row[1], "payAmount": row[0]}} if row else None)

    def list_orders(self, authorization, page_size=10):
        self.read_count += 1
        with self.connect() as db:
            rows = db.execute("SELECT id,amount,status FROM orders ORDER BY id DESC LIMIT ?", (page_size,)).fetchall()
        return GatewayQueryResult(capability="order_query", status="success", data={"orders": [
            {"orderId": row[0], "payAmount": row[1], "orderStatus": row[2]} for row in rows]})

    def create(self, args, context):
        self.calls.append(("create", context.operation_id))
        if self.action_failure:
            return ActionResult(status="failed", message="controlled write dependency rejected")
        with self.connect() as db:
            existing = db.execute("SELECT order_id FROM operations WHERE operation_id=?", (context.operation_id,)).fetchone()
            if existing:
                order_id = existing[0]
            else:
                row = db.execute("SELECT price,stock FROM package WHERE id=?", (args.package_id,)).fetchone()
                if not row or row[1] < args.quantity or row[0] != args.expected_unit_price:
                    return ActionResult(status="failed", message="controlled business validation rejected")
                order_id = 9001 + db.execute("SELECT COUNT(*) FROM operations").fetchone()[0]
                db.execute("INSERT INTO orders VALUES (?,?,?,?,?)", (order_id, args.package_id, args.quantity, row[0] * args.quantity, "WAIT_PAY"))
                db.execute("INSERT INTO operations VALUES (?,?)", (context.operation_id, order_id))
                db.execute("UPDATE package SET stock=stock-? WHERE id=?", (args.quantity, args.package_id))
        if self.timeout_after_commit:
            self.timeout_after_commit = False
            raise httpx.ReadTimeout("controlled response lost after durable write")
        return ActionResult(status="success", data={"orderId": order_id, "orderStatus": "WAIT_PAY"})

    def cancel(self, args, context):
        self.calls.append(("cancel", context.operation_id))
        with self.connect() as db:
            db.execute("UPDATE orders SET status='CANCELED' WHERE id=?", (args.order_id,))
            db.execute("INSERT INTO operations VALUES (?,?) ON CONFLICT DO NOTHING", (context.operation_id, args.order_id))
        return ActionResult(status="success", data={"orderId": args.order_id, "orderStatus": "CANCELED"})

    def pay(self, args, context):
        raise AssertionError("payment is outside this controlled suite")

    def refund(self, args, context):
        raise AssertionError("refund is outside this controlled suite")

    def set_price(self, price):
        with self.connect() as db:
            db.execute("UPDATE package SET price=? WHERE id=1", (price,))

    def stats(self):
        with self.connect() as db:
            writes = db.execute("SELECT COUNT(*) FROM operations").fetchone()[0]
            stock = db.execute("SELECT stock FROM package WHERE id=1").fetchone()[0]
            created = db.execute("SELECT COUNT(*) FROM orders WHERE id>=9001").fetchone()[0]
        return {"business_writes": writes, "stock": stock, "created_orders": created,
                "action_attempts": len(self.calls), "operation_identities": len({key for _, key in self.calls}),
                "read_attempts": self.read_count}

    def matching_order(self, result):
        child = result.get("trade", result)
        order_id = child.get("result", {}).get("data", {}).get("orderId")
        with self.connect() as db:
            row = db.execute("SELECT package_id,quantity,amount,status FROM orders WHERE id=?", (order_id,)).fetchone()
        return bool(row == (1, 2, 3600, "WAIT_PAY"))


class _FixedPlanner:
    def __init__(self, tasks):
        self.tasks = tasks

    def plan(self, message, history=None):
        return ExecutionPlan(run_id="controlled-plan", goal=message, tasks=self.tasks,
                             confidence=1, reason="explicit controlled execution plan", source="rules")

    def replan(self, plan, results):
        return None


def _trade_task(*, depends_on=None):
    return AgentTask(id="controlled_trade", agent="trade", capability="trade.workflow", objective="下单套餐1，2份",
        depends_on=depends_on or [], trade_intent=TradeIntent(action="create", trade_type="NORMAL", package_id=1, quantity=2))


class _ControlledRuntime:
    def __init__(self, path: Path, settings: Settings, case_id: str):
        # Do not inherit a real PostgreSQL URL or tracing/model/network switches.
        self.settings = settings.model_copy(update={
            "runtime_database_url": "", "orchestration_checkpoint_path": str(path / "parent.sqlite"),
            "trade_checkpoint_path": str(path / "child.sqlite"), "llm_router_enabled": False,
            "semantic_router_enabled": False, "llm_api_key": "", "embedding_api_key": "",
            "langsmith_tracing": False, "langsmith_api_key": "", "session_memory_enabled": False,
            "long_term_memory_enabled": False, "es_enabled": False, "milvus_enabled": False,
            "trade_workflow_enabled": True, "tool_retry_backoff_seconds": 0.0,
        })
        self.case_id = case_id
        self.gateway = _ControlledGateway(path / "external-ledger.sqlite")
        self.parent = None
        self.child = None
        self.observations: dict = {}
        try:
            self.open()
        except Exception:
            self.close()
            raise

    def open(self):
        self.child = TradeWorkflowService(self.settings, read_client=self.gateway, action_client=self.gateway)
        registry = {"trade.workflow": CapabilitySpec("trade", controlled_trade_node, execution="workflow")}
        planner = _FixedPlanner([_trade_task()])
        if self.case_id == "controlled_history_correction":
            planner = DynamicPlanner(self.settings)
            # Register the real read path too: an observed read-only routing
            # decision must fail the frozen confirmation expectation, rather
            # than fail because this evaluation omitted a capability.
            def query(state):
                intent = TradeIntent.model_validate(state["trade_intent"])
                result = PurchaseReader(self.gateway, self.settings).query(intent, state["authorization"])
                self.observations.setdefault("read_dependency_results", []).append(bool(result.has_data))
                return query_results_payload([result])
            registry["trade.query"] = CapabilitySpec("trade", query)
            registry["general.respond"] = CapabilitySpec("general", lambda _: {"response": "请明确当前请求。"})
        elif self.case_id == "controlled_upstream_failure":
            def unavailable(_):
                raise ConnectionError("controlled upstream unavailable")
            registry["recommend.run"] = CapabilitySpec("recommend", unavailable)
            planner = _FixedPlanner([
                AgentTask(id="controlled_read", agent="recommend", capability="recommend.run", objective="发现候选套餐"),
                _trade_task(depends_on=["controlled_read"]),
            ])
        self.parent = OrchestrationService(self.settings, planner=planner, registry=registry, trade_service=self.child)

    def close(self):
        try:
            if self.parent is not None:
                self.parent.close()
        finally:
            self.parent = None
            if self.child is not None:
                self.child.close()
                self.child = None

    def restart(self):
        self.close()
        self.open()

    def start(self, **kwargs):
        result = self.parent.turn(**{
            "user_id": _OWNER, "authorization": _AUTH, "session_id": _SESSION,
            "request_id": "start", "message": "下单套餐1，2份", **kwargs,
        })
        self.record("initial", result)
        snapshot = self.parent.graph.get_state(self.parent.config(_OWNER, result["run_id"], _SESSION))
        self.observations["planned_capabilities"] = [task["capability"] for task in snapshot.values.get("plan", {}).get("tasks", [])]
        return result

    def resume(self, pending, **kwargs):
        result = self.parent.turn(**{
            "user_id": _OWNER, "authorization": _AUTH, "session_id": _SESSION,
            "run_id": pending["run_id"], "task_id": (pending.get("interrupt") or {}).get("task_id"),
            "request_id": "confirm", "message": "确认",
            "approved": True,
            "confirmation_id": ((pending.get("trade") or {}).get("interrupt") or {}).get("confirmation_id"),
            **kwargs,
        })
        self.record("latest", result)
        return result

    def record(self, name, result):
        child = result.get("trade") or {}
        preview = ((child.get("interrupt") or {}).get("preview") or {})
        # Allowlist scalar state only; never publish raw requests, IDs, tokens,
        # confirmations, graph checkpoints, arbitrary errors or signed forms.
        self.observations[name] = {
            "parent_status": result.get("execution_status"), "child_status": child.get("status"),
            "interrupt_type": (child.get("interrupt") or {}).get("type"),
            "preview_package": preview.get("package_id"), "preview_order": preview.get("order_id"),
            "preview_quantity": preview.get("quantity"), "preview_total_cents": preview.get("estimated_total_cents"),
            "task_statuses": [entry.get("status") for entry in result.get("task_statuses", [])],
        }


def _denied(action, expected_status):
    try:
        action()
    except HTTPException as exc:
        return exc.status_code == expected_status, exc.status_code
    return False, None


def _scenario(runtime: _ControlledRuntime, inputs: dict) -> dict[str, bool]:
    case_id = inputs["case_id"]
    gateway = runtime.gateway
    if case_id == "controlled_missing_parameters":
        base = {"user_id": _OWNER, "authorization": _AUTH, "session_id": _SESSION}
        initial = runtime.child.turn(**base, request_id="missing", message="帮我下单",
            initial_intent={"action": "create", "trade_type": "NORMAL"})
        second = runtime.child.turn(**base, thread_id=initial["thread_id"], request_id="package", message="套餐1")
        third = runtime.child.turn(**base, thread_id=initial["thread_id"], request_id="quantity", message="2份")
        no_write = gateway.stats()["business_writes"] == 0 and not gateway.calls
        complete = runtime.child.turn(**base, thread_id=third["thread_id"], request_id="approve",
            approved=True, confirmation_id=third["interrupt"]["confirmation_id"])
        runtime.record("latest", {"trade": complete})
        runtime.observations["missing_fields"] = [
            (initial.get("interrupt") or {}).get("missing_fields"),
            (second.get("interrupt") or {}).get("missing_fields")]
        return {"initial_missing_fields": (initial.get("interrupt") or {}).get("missing_fields") == ["package_id", "quantity"],
            "quantity_still_required": (second.get("interrupt") or {}).get("missing_fields") == ["quantity"],
            "exact_preview_after_completion": (third.get("interrupt") or {}).get("preview", {}).get("estimated_total_cents") == 3600,
            "no_write_before_confirmation": no_write, "completed_after_confirmation": complete.get("status") == "completed",
            "matching_order_persisted": gateway.matching_order(complete)}

    if case_id == "controlled_auth_required":
        parent_denied, parent_code = _denied(lambda: runtime.start(authorization=""), 401)
        child_denied, child_code = _denied(lambda: runtime.child.turn(user_id=_OWNER, authorization="", session_id=_SESSION,
            message="下单套餐1，2份"), 401)
        runtime.observations["http_statuses"] = [parent_code, child_code]
        return {"parent_unauthorized_denied": parent_denied, "child_unauthorized_denied": child_denied,
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}

    if case_id == "controlled_read_failure":
        gateway.read_unavailable = True
    if case_id == "controlled_history_correction":
        pending = runtime.start(message=inputs.get("message", "不是订单42，改为取消订单52"),
            session_history=[{"user": "查询订单42", "assistant": "订单42待付款", "intent": "trade"}])
    else:
        pending = runtime.start()
    child = pending.get("trade") or {}
    interrupt = child.get("interrupt") or {}
    preview = interrupt.get("preview") or {}

    if case_id == "controlled_preview":
        return {"confirmation_required": child.get("status") == "awaiting_confirmation" and pending.get("execution_status") == "waiting_input",
            "exact_preview": all(preview.get(k) == v for k, v in {"package_id": 1, "quantity": 2, "unit_price_cents": 1800, "estimated_total_cents": 3600}.items()),
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_history_correction":
        return {"confirmation_required": child.get("status") == "awaiting_confirmation",
            "latest_order_selected": preview.get("order_id") == 52, "old_order_not_selected": preview.get("order_id") != 42,
            "read_dependencies_available": bool(runtime.observations.get("read_dependency_results")) and all(runtime.observations["read_dependency_results"]),
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_read_failure":
        return {"child_failed": child.get("status") == "failed", "parent_not_completed": pending.get("execution_status") in {"failed", "partial"},
            "no_confirmation_offered": child.get("interrupt") is None,
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_upstream_failure":
        statuses = {entry["task_id"]: entry["status"] for entry in pending.get("task_results", [])}
        return {"upstream_failed": statuses.get("controlled_read") == "failed", "downstream_skipped": statuses.get("controlled_trade") == "skipped",
            "no_child_started": child == {}, "parent_not_completed": pending.get("execution_status") in {"failed", "partial"},
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_wrong_confirmation":
        denied, code = _denied(lambda: runtime.resume(pending, confirmation_id="invalid-controlled-confirmation"), 409)
        runtime.observations["http_status"] = code
        current = runtime.parent.view(_OWNER, pending["run_id"], _SESSION)
        return {"confirmation_denied": denied, "confirmation_still_pending": (current.get("trade") or {}).get("status") == "awaiting_confirmation",
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id in {"controlled_ownership", "controlled_session"}:
        owner_case = case_id == "controlled_ownership"
        overrides = {"user_id": "different-owner"} if owner_case else {"session_id": "different-session"}
        expected = 404 if owner_case else 409
        parent_denied, parent_code = _denied(lambda: runtime.resume(pending, **overrides), expected)
        child_denied, child_code = _denied(lambda: runtime.child.turn(**{
            "user_id": _OWNER, "authorization": _AUTH, "session_id": _SESSION, "thread_id": child["thread_id"],
            "request_id": "foreign-confirm", "approved": True, "confirmation_id": interrupt["confirmation_id"], **overrides}), expected)
        runtime.observations["http_statuses"] = [parent_code, child_code]
        return {("parent_owner_denied" if owner_case else "parent_session_denied"): parent_denied,
            ("child_owner_denied" if owner_case else "child_session_denied"): child_denied,
            "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_payload_conflict":
        denied, code = _denied(lambda: runtime.start(run_id=pending["run_id"], message="下单套餐1，3份"), 409)
        runtime.observations["http_status"] = code
        return {"payload_conflict_denied": denied, "no_business_write": gateway.stats()["business_writes"] == 0 and not gateway.calls}
    if case_id == "controlled_restart":
        runtime.restart()
        restored = runtime.parent.view(_OWNER, pending["run_id"], _SESSION)
        same = (restored.get("trade") or {}).get("interrupt") == interrupt
        complete = runtime.resume(restored)
        return {"same_pending_checkpoint": same, "parent_completed": complete.get("execution_status") == "completed",
            "matching_order_persisted": gateway.matching_order(complete), "one_business_write": gateway.stats()["business_writes"] == 1}
    if case_id == "controlled_response_loss":
        remember = runtime.child.storage.remember
        def lose_receipt(*args, **kwargs):
            raise RuntimeError("controlled receipt loss after checkpoint")
        runtime.child.storage.remember = lose_receipt
        receipt_loss = False
        try:
            runtime.resume(pending)
        except RuntimeError:
            receipt_loss = True
        finally:
            runtime.child.storage.remember = remember
        saved = runtime.child.view(_OWNER, child["thread_id"])
        runtime.restart()
        complete = runtime.resume(pending)
        repeated = runtime.resume(pending)
        runtime.record("after_restart_replay", complete)
        try:
            recovered = runtime.resume(pending, request_id="recover-receipt-loss", message="", approved=None,
                confirmation_id=None, recover=True)
        except Exception as exc:  # noqa: BLE001 - retain a production graph failure without erasing verified side-effects.
            # A production recovery defect must remain a failed assertion while
            # retaining independently observed receipt/idempotency safety facts.
            runtime.observations["recovery_error_type"] = type(exc).__name__
            if type(exc).__name__ == "InvalidUpdateError" and "last_request_id" in str(exc):
                runtime.observations["recovery_conflicting_channel"] = "last_request_id"
            recovered = {}
        return {"receipt_loss_observed": receipt_loss, "completed_checkpoint_survived": saved.get("status") == "completed",
            "same_response_after_restart": complete == repeated and (complete.get("trade") or {}).get("result") == saved.get("result"),
            "parent_completed_after_explicit_recovery": recovered.get("execution_status") == "completed" and (recovered.get("trade") or {}).get("status") == "completed",
            "matching_order_persisted": gateway.matching_order(saved), "one_business_write": gateway.stats()["business_writes"] == 1,
            "one_action_attempt": len(gateway.calls) == 1}

    if case_id == "controlled_stale_preview":
        gateway.set_price(1900)
    elif case_id == "controlled_action_failure":
        gateway.action_failure = True
    elif case_id == "controlled_timeout_recovery":
        gateway.timeout_after_commit = True
    complete = runtime.resume(pending, approved=case_id != "controlled_rejection")
    stats = gateway.stats()
    if case_id == "controlled_approval":
        return {"parent_completed": complete.get("execution_status") == "completed", "child_completed": (complete.get("trade") or {}).get("status") == "completed",
            "matching_order_persisted": gateway.matching_order(complete), "one_business_write": stats["business_writes"] == 1,
            "stock_decremented_once": stats["stock"] == 18}
    if case_id == "controlled_rejection":
        return {"child_rejected": (complete.get("trade") or {}).get("status") == "rejected", "no_business_write": stats["business_writes"] == 0 and not gateway.calls,
            "stock_unchanged": stats["stock"] == 20}
    if case_id == "controlled_replay":
        replay = runtime.resume(pending)
        another = runtime.resume(pending, request_id="second-confirm")
        stats = gateway.stats()
        return {"same_request_replayed": replay == complete, "new_request_same_result": another.get("trade") == complete.get("trade"),
            "one_business_write": stats["business_writes"] == 1, "one_operation_identity": stats["operation_identities"] == 1,
            "one_action_attempt": stats["action_attempts"] == 1}
    if case_id == "controlled_timeout_recovery":
        uncertain = (complete.get("trade") or {}).get("status") == "uncertain"
        waiting = complete.get("execution_status") == "waiting_external"
        runtime.record("after_timeout", complete)
        runtime.restart()
        recovered = runtime.resume(pending, request_id="recover", message="", approved=None, confirmation_id=None, recover=True)
        stats = gateway.stats()
        return {"uncertain_after_transport_timeout": uncertain, "waiting_external": waiting,
            "completed_after_restart_recovery": recovered.get("execution_status") == "completed" and gateway.matching_order(recovered),
            "same_child_workflow": (recovered.get("trade") or {}).get("thread_id") == child.get("thread_id"),
            "one_operation_identity": stats["operation_identities"] == 1, "one_business_write": stats["business_writes"] == 1,
            "two_action_attempts": stats["action_attempts"] == 2, "stock_decremented_once": stats["stock"] == 18}
    if case_id == "controlled_stale_preview":
        return {"stale_price_detected": (complete.get("trade") or {}).get("status") == "stale",
            "parent_not_completed": complete.get("execution_status") in {"failed", "partial"}, "no_business_write": stats["business_writes"] == 0 and not gateway.calls,
            "stock_unchanged": stats["stock"] == 20}
    if case_id == "controlled_action_failure":
        return {"child_failed": (complete.get("trade") or {}).get("status") == "failed", "parent_not_completed": complete.get("execution_status") in {"failed", "partial"},
            "one_action_attempt": stats["action_attempts"] == 1, "no_business_write": stats["business_writes"] == 0,
            "stock_unchanged": stats["stock"] == 20}
    raise ValueError("unsupported controlled scenario")


def run_case(inputs: dict, settings: Settings) -> dict:
    """Run one independently cleaned scenario and return only sanitized evidence.

    Unexpected exceptions fail all references; error text is never returned.
    Observed product mismatches stay false, without changing product behavior.
    """
    case = next((case for case in load_cases() if case["id"] == inputs.get("case_id")), None)
    if case is None:
        raise ValueError("unknown controlled case")
    started = perf_counter()
    result = {"checks": {name: False for name in case["outputs"]["required_checks"]},
        "unverified_checks": list(case["outputs"]["required_checks"]),
        "evidence": {"external_dependencies": "controlled_sqlite_gateway", "planner": "rules" if case["category"] == "memory" else "controlled_plan",
                     "real_payment": False, "resources_closed": False}, "execution_tier": "controlled_runtime"}
    runtime = None
    temporary_path = None
    try:
        with TemporaryDirectory(prefix="food-agent-controlled-") as temporary:
            temporary_path = Path(temporary)
            try:
                runtime = _ControlledRuntime(Path(temporary), settings, case["id"])
                observed = _scenario(runtime, inputs)
                # Exact key matching prevents a forgotten check from silently passing.
                if set(observed) != set(result["checks"]) or any(type(value) is not bool for value in observed.values()):
                    raise ValueError("controlled check contract mismatch")
                result["checks"] = observed
                result["unverified_checks"] = []
                if runtime.observations.get("recovery_error_type"):
                    result["error"] = runtime.observations["recovery_error_type"]
            finally:
                if runtime is not None:
                    try:
                        result["evidence"].update(runtime.observations)
                        result["evidence"]["ledger"] = runtime.gateway.stats()
                    finally:
                        runtime.close()
        result["evidence"]["resources_closed"] = True
    except Exception as exc:  # noqa: BLE001 - evaluation boundary preserves type, never arbitrary detail.
        result["error"] = type(exc).__name__
        result["evidence"]["resources_closed"] = bool(temporary_path is not None and not temporary_path.exists()
            and (runtime is None or (runtime.parent is None and runtime.child is None)))
    result["duration_ms"] = round((perf_counter() - started) * 1000, 3)
    return result

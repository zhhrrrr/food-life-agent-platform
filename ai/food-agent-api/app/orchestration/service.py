"""Durable parent runs, authenticated resume, checkpoint-boundary pause and replayable SSE."""
from __future__ import annotations

import hashlib
import json
import logging
import threading
from functools import lru_cache
from uuid import uuid4

from fastapi import HTTPException
from langgraph.types import Command

from app.core.config import get_settings
from app.core.langsmith import finish_trace, observation_scope
from app.core.persistence import RuntimeDatabase
from app.graph.food_graph import build_food_graph, execution_view
from app.orchestration.planner import DynamicPlanner
from app.orchestration.registry import CAPABILITY_REGISTRY
from app.orchestration.trade_adapter import TradeWorkflowAdapter
from app.trade.storage import TradeStorage
from app.tools.runtime import ToolCapacityExceeded, ToolPool

logger = logging.getLogger("food_agent.orchestration.service")


class OrchestrationService:
    def __init__(self, settings, *, planner=None, registry=None, trade_service=None):
        self.settings = settings
        self.database = RuntimeDatabase(settings.runtime_database_url or settings.orchestration_checkpoint_path)
        self.path = self.database.location
        self.storage = TradeStorage(self.path, database=self.database)
        from app.memory.delivery import MemoryDelivery
        self.memory_delivery = MemoryDelivery(settings, database=self.database)
        with self.db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS agent_runs (
                  run_key TEXT PRIMARY KEY, run_id TEXT NOT NULL, user_id TEXT NOT NULL,
                  session_id TEXT NOT NULL, input_json TEXT NOT NULL, paused INTEGER NOT NULL DEFAULT 0,
                  error TEXT NOT NULL DEFAULT '');
                CREATE TABLE IF NOT EXISTS agent_run_events (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, run_key TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL);
                CREATE INDEX IF NOT EXISTS idx_agent_run_events ON agent_run_events(run_key,id);
            """)
        self.adapter = TradeWorkflowAdapter(settings, trade_service)
        self.graph = build_food_graph(settings, self.database.checkpointer(), planner=planner or DynamicPlanner(settings),
                                      registry=registry if registry is not None else CAPABILITY_REGISTRY, adapter=self.adapter)
        self.pool = ToolPool(4, thread_name_prefix="agent-run")
        self.futures = {}
        self.future_requests = {}
        self.future_lock = threading.Lock()

    def db(self):
        return self.database.connect()

    def _remember(self, key, request_id, payload, response):
        meta = self._meta(key)
        with self.db() as db:
            db.execute("UPDATE trade_requests SET response=? WHERE thread_id=? AND request_id=?",
                       (json.dumps(response, ensure_ascii=False), key, request_id))
            message = payload.get("message", "") or ("确认交易" if payload.get("approved") is True else "拒绝交易" if payload.get("approved") is False else "恢复原任务" if payload.get("recover") else "")
            self.memory_delivery.enqueue_result(meta[1], meta[2], request_id, message, response, db=db)

    @staticmethod
    def config(user_id: str, run_id: str, session_id: str = "") -> dict:
        owner = user_id or "anonymous:" + session_id
        return {"configurable": {"thread_id": "agent:" + hashlib.sha256(owner.encode()).hexdigest() + ":" + run_id}, "recursion_limit": 100}

    def _meta(self, key):
        with self.db() as db:
            row = db.execute("SELECT run_id,user_id,session_id,input_json,paused,error FROM agent_runs WHERE run_key=?", (key,)).fetchone()
        if not row:
            raise HTTPException(404, "协作运行不存在。")
        return row

    def ensure(self, *, user_id, session_id, request_id, message="", session_history=None, run_id=None, **unused):
        if run_id is None:
            run_id = hashlib.sha256(f"{user_id}:{session_id}:{request_id}".encode()).hexdigest()[:32]
            initial = {"message": message, "session_history": [t.model_dump() if hasattr(t, "model_dump") else t for t in session_history or []]}
            key = self.config(user_id, run_id, session_id)["configurable"]["thread_id"]
            with self.db() as db:
                db.execute("INSERT INTO agent_runs(run_key,run_id,user_id,session_id,input_json) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                           (key, run_id, user_id, session_id, json.dumps(initial, ensure_ascii=False)))
        key = self.config(user_id, run_id, session_id)["configurable"]["thread_id"]
        meta = self._meta(key)
        if meta[2] != session_id:
            raise HTTPException(409, "协作运行与会话不匹配。")
        return run_id, key

    def emit(self, key, kind, payload):
        with self.db() as db:
            db.execute("INSERT INTO agent_run_events(run_key,kind,payload) VALUES (?,?,?)", (key, kind, json.dumps(payload, ensure_ascii=False)))

    def events(self, user_id, run_id, after=0):
        key = self.config(user_id, run_id)["configurable"]["thread_id"]
        self._meta(key)
        with self.db() as db:
            rows = db.execute("SELECT id,kind,payload FROM agent_run_events WHERE run_key=? AND id>? ORDER BY id LIMIT 100", (key, after)).fetchall()
        return [{"id": row[0], "event": row[1], "data": json.loads(row[2])} for row in rows]

    def view(self, user_id, run_id, session_id=""):
        config = self.config(user_id, run_id, session_id)
        key = config["configurable"]["thread_id"]
        meta = self._meta(key)
        snapshot = self.graph.get_state(config)
        state = snapshot.values
        result = execution_view(state) if state.get("plan") else {
            "run_id": run_id, "plan_revision": 1, "workflow": "persistent_dag", "execution_status": "running",
            "intent": "general", "agent": "general", "agents": [], "response": "正在分析和执行请求。", "references": [], "task_results": [], "rag_mode": ""}
        result.update(session_id=meta[2], router_source=state.get("plan", {}).get("source", "pending"),
                      router_confidence=state.get("plan", {}).get("confidence", 0.0),
                      router_reason=state.get("plan", {}).get("reason", ""), memory_status="checkpoint")
        pending = next((i.value for task in snapshot.tasks for i in task.interrupts), None)
        if pending and pending.get("type") == "trade" and pending.get("trade"):
            try:
                current_trade = self.adapter.service.view(user_id, pending["trade"]["thread_id"])
                pending = {**pending, "trade": current_trade, "message": current_trade["response"]}
            except HTTPException:
                pass
        result["interrupt"] = pending
        if pending:
            result["response"] = pending["message"] if pending["type"] == "reference" else (result["response"] + "\n\n" + pending["message"]).strip()
            result["execution_status"] = "waiting_external" if pending.get("trade", {}).get("status") in {"awaiting_result", "uncertain", "unauthorized", "running"} else "waiting_input"
        elif snapshot.next:
            result["execution_status"] = "running"
        if meta[4]:
            result["execution_status"] = "paused" if pending else "pausing"
        if meta[5]:
            result["execution_status"] = "interrupted"
            result["response"] = "运行暂未完成，请恢复同一运行核对结果。"
        trades = [item["payload"]["trade"] for item in state.get("results", {}).values() if item.get("payload", {}).get("trade")]
        result["purchase_options"] = [option for item in state.get("results", {}).values() for option in item.get("artifact", {}).get("purchase_options", [])]
        result["trade"] = pending.get("trade") if pending and pending.get("trade") else trades[-1] if trades else None
        result["task_statuses"] = [{"task_id": item["task_id"], "agent": item["agent"], "status": item["status"], "attempts": item["attempts"]} for item in result.get("task_results", [])]
        return result

    def turn(self, *, user_id: str, authorization: str, session_id: str, request_id: str | None = None,
             message: str = "", session_history=None, run_id=None, task_id=None, approved=None, confirmation_id=None, recover=False):
        request_id = request_id or uuid4().hex
        run_id, key = self.ensure(user_id=user_id, session_id=session_id, request_id=request_id, message=message,
                                  session_history=session_history, run_id=run_id)
        config = self.config(user_id, run_id, session_id)
        payload = {"message": message, "session_id": session_id, "task_id": task_id, "approved": approved,
                   "confirmation_id": confirmation_id, "recover": recover}
        with self.storage.lease(key) as lease_check:
            replay = self.storage.replay(key, request_id, payload)
            if replay:
                return replay
            self.storage.reserve(key, request_id, payload)
            snapshot = self.graph.get_state(config)
            meta = self._meta(key)
            pending = next((i.value for task in snapshot.tasks for i in task.interrupts), None)
            ctx = {"authorization": authorization, "request_id": request_id, "lease_check": lease_check,
                   "paused": lambda: bool(self._meta(key)[4]), "emit": lambda kind, data: self.emit(key, kind, {**data, "request_id": request_id})}
            with self.db() as db:
                db.execute("UPDATE agent_runs SET error='' WHERE run_key=?", (key,))
            if recover:
                with self.db() as db:
                    db.execute("UPDATE agent_runs SET paused=0 WHERE run_key=?", (key,))
            elif meta[4]:
                raise HTTPException(409, "运行已暂停，请先恢复。")
            command = None
            if snapshot.values.get("last_request_id") == request_id:
                if not snapshot.next or pending:
                    response = self.view(user_id, run_id, session_id)
                    self._remember(key, request_id, payload, response)
                    return response
            elif not snapshot.values.get("plan") and not snapshot.values:
                if approved is not None or confirmation_id is not None:
                    raise HTTPException(409, "没有可确认的交易预览。")
                initial = json.loads(meta[3])
                if not initial["message"].strip():
                    raise HTTPException(422, "请提供请求。")
                command = {**initial, "user_id": user_id, "session_id": session_id, "run_id": run_id, "last_request_id": request_id}
            elif pending:
                if task_id and task_id != pending.get("task_id"):
                    raise HTTPException(409, "回复与等待任务不匹配。")
                if pending["type"] == "paused":
                    if not recover or approved is not None:
                        raise HTTPException(409, "请恢复暂停的运行。")
                elif pending["type"] == "reference":
                    if recover and not message:
                        return self.view(user_id, run_id, session_id)
                    if not message.strip() or approved is not None or confirmation_id is not None:
                        raise HTTPException(422, "请明确要查询或操作的对象，补充对象不能代替交易确认。")
                elif pending["type"] == "selection":
                    if recover and not message:
                        return self.view(user_id, run_id, session_id)
                    if approved is not None or confirmation_id is not None:
                        raise HTTPException(422, "选择对象不能代替交易确认。")
                    self.adapter.select(pending, message)  # validate before persisting Command
                else:
                    child = pending["trade"]
                    current_child = self.adapter.service.view(user_id, child["thread_id"])
                    already_finished = current_child["status"] in {"completed", "failed", "rejected", "expired", "stale", "clarification"}
                    interrupt_type = (current_child.get("interrupt") or {}).get("type")
                    if recover and not already_finished and current_child["status"] in {"awaiting_parameters", "awaiting_confirmation"}:
                        return self.view(user_id, run_id, session_id)
                    if not (recover and already_finished) and interrupt_type == "confirmation" and (approved is None or confirmation_id != current_child["interrupt"]["confirmation_id"]):
                        raise HTTPException(409, "请携带当前确认标识和明确决定。")
                    if interrupt_type == "parameters" and (not message.strip() or approved is not None):
                        raise HTTPException(422, "请补充交易参数。")
                    if current_child["status"] in {"uncertain", "unauthorized", "running", "awaiting_result"} and (not recover or approved is not None):
                        raise HTTPException(409, "请恢复并核对原交易结果。")
                command = Command(resume={"message": message, "approved": approved, "confirmation_id": confirmation_id,
                                          "recover": recover})
            elif not snapshot.next:
                response = self.view(user_id, run_id, session_id)
                if approved is not None and (not response.get("trade") or confirmation_id != (response["trade"].get("interrupt") or {}).get("confirmation_id")):
                    # Completed children have no interrupt; consult their checkpoint's original confirmation.
                    child = response.get("trade")
                    original = self.adapter.service.graph.get_state(self.adapter.service.config(user_id, child["thread_id"])) if child else None
                    if not original or confirmation_id != original.values.get("confirmation_id"):
                        raise HTTPException(409, "运行已经结束。")
                self._remember(key, request_id, payload, response)
                return response
            elif not recover and snapshot.values.get("last_request_id") != request_id:
                raise HTTPException(409, "请恢复原运行。")
            try:
                with observation_scope(self.settings, "food_agent.turn", inputs={"message": message, "recover": recover},
                        metadata={"business_run_id": run_id, "request_id": request_id,
                                  "thread_id": key, "session_id": session_id}) as trace_run:
                    state = self.graph.invoke(command, config, context=ctx, durability="sync")
                    finish_trace(trace_run, {"phase": state.get("phase"), "plan_source": state.get("plan", {}).get("source"),
                                             "replan_count": state.get("replan_count", 0)})
            except Exception as exc:
                with self.db() as db:
                    db.execute("UPDATE agent_runs SET error=? WHERE run_key=?", (type(exc).__name__, key))
                self.emit(key, "error", {"run_id": run_id, "request_id": request_id, "message": "运行中断，可恢复原运行。"})
                raise
            response = self.view(user_id, run_id, session_id)
            response["request_id"] = request_id
            self._remember(key, request_id, payload, response)
            self.emit(key, "snapshot", response)
            self.emit(key, "turn_finished", {"status": response["execution_status"], "request_id": request_id})
            return response

    def submit(self, **kwargs):
        kwargs["request_id"] = kwargs.get("request_id") or uuid4().hex
        if not kwargs.get("run_id") and (kwargs.get("approved") is not None or kwargs.get("confirmation_id") is not None):
            raise HTTPException(409, "没有可确认的交易预览。")
        run_id, key = self.ensure(**{k: v for k, v in kwargs.items() if k != "authorization"})
        with self.future_lock:
            current = self.futures.get(key)
            identity = self.storage.fingerprint({k: v for k, v in kwargs.items() if k not in {"authorization", "session_history"}})
            if current and not current.done():
                if self.future_requests.get(key) != identity:
                    raise HTTPException(409, "运行正在执行，请订阅进度或稍后恢复。")
                return {"run_id": run_id, "session_id": kwargs["session_id"], "request_id": kwargs["request_id"], "execution_status": "running"}
            def run():
                try:
                    result = self.turn(**kwargs)
                    return result
                except Exception as exc:  # noqa: BLE001 - persist an execution error before propagating
                    self.emit(key, "error", {"run_id": run_id, "request_id": kwargs["request_id"],
                        "message": str(exc.detail) if isinstance(exc, HTTPException) else "运行中断，请恢复同一运行。"})
                    return None
            try:
                future = self.pool.submit(run)
            except ToolCapacityExceeded:
                raise HTTPException(503, "运行容量已满，请稍后重试。") from None
            self.futures[key] = future
            self.future_requests[key] = identity
        def forget(done):
            with self.future_lock:
                if self.futures.get(key) is done:
                    self.futures.pop(key, None)
                    self.future_requests.pop(key, None)
        # A completed Future can invoke its callback immediately; never hold future_lock here.
        future.add_done_callback(forget)
        return {"run_id": run_id, "session_id": kwargs["session_id"], "request_id": kwargs["request_id"], "execution_status": "running"}

    def pause(self, user_id, run_id):
        key = self.config(user_id, run_id)["configurable"]["thread_id"]
        current = self.view(user_id, run_id)
        if current["execution_status"] in {"completed", "partial", "failed"}:
            raise HTTPException(409, "运行已结束。")
        with self.db() as db:
            db.execute("UPDATE agent_runs SET paused=1 WHERE run_key=?", (key,))
        self.emit(key, "pause_requested", {"run_id": run_id})
        return self.view(user_id, run_id)

    def close(self):
        self.pool.close(wait=True)
        self.database.close()


@lru_cache(maxsize=1)
def get_orchestration_service():
    return OrchestrationService(get_settings())


def close_orchestration_service():
    if get_orchestration_service.cache_info().currsize:
        get_orchestration_service().close()
        get_orchestration_service.cache_clear()

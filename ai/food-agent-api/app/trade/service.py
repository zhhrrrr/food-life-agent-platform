from __future__ import annotations

import hashlib
from functools import lru_cache
from uuid import uuid4

from fastapi import HTTPException
from langgraph.types import Command

from app.core.config import Settings, get_settings
from app.core.langsmith import finish_trace, observation_scope
from app.core.persistence import RuntimeDatabase
from app.tools.budget import tool_budgeted
from app.trade.storage import TradeStorage
from app.trade.workflow import build_trade_graph


class TradeWorkflowService:
    def __init__(self, settings: Settings, **graph_options):
        self.settings = settings
        self.database = RuntimeDatabase(settings.runtime_database_url or settings.trade_checkpoint_path)
        self.storage = TradeStorage(self.database.location, database=self.database)
        self.checkpointer = self.database.checkpointer()
        self.graph = build_trade_graph(settings, self.checkpointer, self.storage, **graph_options)

    def close(self):
        self.database.close()

    @staticmethod
    def config(user_id: str, thread_id: str) -> dict:
        # Owner namespace is derived from trusted Gateway identity, not client input.
        owner = hashlib.sha256(user_id.encode()).hexdigest()
        return {"configurable": {"thread_id": f"trade:{owner}:{thread_id}"}, "recursion_limit": 30}

    @staticmethod
    def _snapshot_view(snapshot, thread_id: str) -> dict:
        if not snapshot.values:
            raise HTTPException(404, "交易工作流不存在。")
        state = snapshot.values
        pending = next((item.value for task in snapshot.tasks for item in task.interrupts), None)
        status = ({"parameters": "awaiting_parameters", "confirmation": "awaiting_confirmation",
                   "seckill_wait": "awaiting_result"}.get(pending["type"], "running")
                  if pending else state.get("status", "running"))
        return {"thread_id": thread_id, "session_id": state["session_id"], "status": status,
                "action": state.get("intent", {}).get("action", "unknown"),
                "trade_type": state.get("intent", {}).get("trade_type"),
                "response": pending["message"] if pending else state.get("response", ""),
                "interrupt": pending, "result": state.get("result", {})}

    def view(self, user_id: str, thread_id: str) -> dict:
        return self._snapshot_view(self.graph.get_state(self.config(user_id, thread_id)), thread_id)

    @staticmethod
    def _resume_input(pending: dict, *, message: str, approved: bool | None,
                      confirmation_id: str | None, recover: bool) -> dict:
        """校验当前中断允许的输入，并构造恢复载荷。"""
        if pending["type"] == "seckill_wait":
            if approved is not None or confirmation_id is not None:
                raise HTTPException(422, "秒杀等待结果只接受刷新请求。")
            return {"poll": True}
        if recover:
            raise HTTPException(409, "请先完成参数补全和确认。")
        if pending["type"] == "parameters":
            if not message.strip() or approved is not None or confirmation_id is not None:
                raise HTTPException(422, "请补充交易参数。")
            return {"message": message}
        if approved is None or confirmation_id != pending["confirmation_id"]:
            raise HTTPException(409, "请携带当前 confirmation_id 和明确的 approved 决定。")
        return {"approved": approved, "confirmation_id": confirmation_id}

    def _advance(self, snapshot, config: dict, context: dict, *, thread_id: str,
                 session_id: str, message: str, request_id: str,
                 confirmation_id: str | None, approved: bool | None,
                 initial_intent: dict | None) -> None:
        """根据检查点选择启动、回答中断或恢复；所有路径共用一次图调用。"""
        command = None
        if snapshot.values.get("last_request_id") == request_id:
            # 检查点已保存、HTTP 回执未保存：仅继续未完成执行，不再次消费用户输入。
            pending = any(task.interrupts for task in snapshot.tasks)
            if not snapshot.next or pending:
                return
        elif not snapshot.values:
            if confirmation_id is not None or approved is not None or context["recover"]:
                raise HTTPException(404, "交易工作流不存在。")
            if not message.strip():
                raise HTTPException(422, "请提供交易请求。")
            command = {"user_id": context["user_id"], "session_id": session_id, "message": message,
                       "last_request_id": request_id, "initial_intent": initial_intent or {}}
        else:
            if snapshot.values["session_id"] != session_id:
                raise HTTPException(409, "交易工作流与会话不匹配。")
            current = self._snapshot_view(snapshot, thread_id)
            if current["interrupt"]:
                resume = self._resume_input(current["interrupt"], message=message, approved=approved,
                                            confirmation_id=confirmation_id, recover=context["recover"])
                command = Command(resume=resume, update={"last_request_id": request_id})
            elif context["recover"] and (
                (snapshot.values.get("approved") and current["status"] in {"uncertain", "unauthorized", "running"})
                or (snapshot.next and current["status"] == "running")
            ):
                # 已确认但已走到 END 的不确定动作，沿用原参数和 operation_id 回到 execute。
                if not snapshot.next:
                    self.graph.update_state(config, {"status": "running", "last_request_id": request_id},
                                            as_node="confirm")
            elif (current["status"] == "completed" and approved is True
                  and confirmation_id == snapshot.values.get("confirmation_id")):
                return
            else:
                raise HTTPException(409, "该工作流已结束或不能恢复，请发起新交易。")
        with observation_scope(self.settings, "trade.turn", inputs={"message": message, "approved": approved},
                metadata={"trade_thread_id": thread_id, "request_id": request_id, "session_id": session_id}) as trace_run:
            state = self.graph.invoke(command, config, context=context, durability="sync")
            finish_trace(trace_run, {"status": state.get("status"), "action": state.get("intent", {}).get("action")})

    @tool_budgeted
    def turn(
        self, *,
        user_id: str,
        authorization: str,
        session_id: str,
        message: str = "",
        thread_id: str | None = None,
        request_id: str | None = None,
        confirmation_id: str | None = None,
        approved: bool | None = None,
        recover: bool = False,
        initial_intent: dict | None = None,
    ) -> dict:
        if not self.settings.trade_workflow_enabled:
            raise HTTPException(503, "交易工作流已禁用。")
        if not user_id or not authorization.strip():
            raise HTTPException(401, "交易工作流需要经过Gateway鉴权的登录用户。")
        request_id = request_id or uuid4().hex
        thread_id = thread_id or hashlib.sha256(f"{user_id}:{session_id}:{request_id}".encode()).hexdigest()[:32]
        config = self.config(user_id, thread_id)
        key = config["configurable"]["thread_id"]
        context = {"user_id": user_id, "authorization": authorization, "recover": recover}
        payload = {"session_id": session_id, "message": message, "confirmation_id": confirmation_id,
                   "approved": approved, "recover": recover}
        if initial_intent is not None:
            payload["initial_intent"] = initial_intent

        with self.storage.lease(key) as lease_check:
            context["lease_check"] = lease_check
            replay = self.storage.replay(key, request_id, payload)
            if replay:
                return replay
            self.storage.reserve(key, request_id, payload)

            snapshot = self.graph.get_state(config)
            self._advance(snapshot, config, context, thread_id=thread_id, session_id=session_id,
                          message=message, request_id=request_id, confirmation_id=confirmation_id,
                          approved=approved, initial_intent=initial_intent)

            # 每轮统一读取最新状态并保存回执，包括秒杀刷新和丢失回执后的重放。
            response = self.view(user_id, thread_id)
            response["request_id"] = request_id
            self.storage.remember(key, request_id, payload, response)
            return response


@lru_cache(maxsize=1)
def get_trade_service() -> TradeWorkflowService:
    return TradeWorkflowService(get_settings())


def close_trade_service() -> None:
    if get_trade_service.cache_info().currsize:
        get_trade_service().close()
        get_trade_service.cache_clear()

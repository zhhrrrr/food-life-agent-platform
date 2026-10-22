"""Typed handoff to the existing confirmed Trade workflow; no worker write retries."""
from __future__ import annotations

import hashlib
import re

from fastapi import HTTPException

from app.core.config import Settings
from app.orchestration.models import AgentTask, TaskArtifact, TaskResult
from app.trade.intent import TradeIntent, parse_intent
from app.trade.service import get_trade_service


class TradeWorkflowAdapter:
    def __init__(self, settings: Settings, trade_service=None):
        self.settings = settings
        self._service = trade_service

    @property
    def service(self):
        return self._service or get_trade_service()

    def prepare(self, task: AgentTask, results: dict[str, TaskResult], resolved: dict) -> tuple[AgentTask, dict | None]:
        intent = TradeIntent.model_validate(resolved[task.id]) if task.id in resolved else (
            task.trade_intent or parse_intent(task.objective, {}, self.settings.model_copy(update={"llm_router_enabled": False})))
        dependencies = [results[d].artifact for d in task.depends_on]
        candidates = list(dict.fromkeys(i for artifact in dependencies for i in artifact.package_ids))
        orders = list(dict.fromkeys(i for artifact in dependencies for i in artifact.order_ids))
        for field, ref in task.input_refs.items():
            producer, name = ref.split(".")
            values = getattr(results[producer].artifact, name)
            if len(values) == 1:
                if getattr(intent, field) is not None and getattr(intent, field) != values[0]:
                    raise HTTPException(409, "上游参数与用户明确指定的对象不一致。")
                intent = intent.model_copy(update={field: values[0]})
        if intent.action in {"pay", "cancel", "refund"} and intent.order_id is None and len(orders) == 1:
            intent = intent.model_copy(update={"order_id": orders[0]})
        needs_package = task.capability == "trade.advise" or (intent.action == "create" and intent.trade_type != "SECKILL")
        if needs_package and intent.package_id is None:
            if len(candidates) == 1:
                intent = intent.model_copy(update={"package_id": candidates[0]})
            elif candidates:
                return task.model_copy(update={"trade_intent": intent}), {
                    "type": "selection", "task_id": task.id, "field": "package_id", "draft": intent.model_dump(),
                    "choices": [{"message": f"套餐{i}", "label": f"套餐{i}", "value": i} for i in candidates],
                    "message": "请从推荐候选中选择套餐ID，选择后再核对实时购买预览。"}
        if task.capability == "trade.advise" and intent.package_id is None:
            return task.model_copy(update={"trade_intent": intent}), {"type": "selection", "task_id": task.id,
                "field": "package_id", "draft": intent.model_dump(), "choices": [], "message": "请提供要比较购买方式的套餐ID。"}
        options = [option for artifact in dependencies for option in artifact.purchase_options]
        if task.capability == "trade.workflow" and intent.action == "create" and intent.trade_type is None and options:
            choices = []
            for index, option in enumerate(options, 1):
                choice = parse_intent(option["message"], {}, self.settings.model_copy(update={"llm_router_enabled": False}))
                if choice.action != "create":
                    continue
                choices.append({"message": f"方案{index}", "label": f"{option['trade_type']}，参考金额{option['price_cents'] / 100:.2f}元",
                                "intent": choice.model_dump()})
            return task.model_copy(update={"trade_intent": intent}), {"type": "selection", "task_id": task.id,
                "field": "purchase_option", "draft": intent.model_dump(), "choices": choices, "message": "请选择购买方案，随后仍需确认具体操作及金额。"}
        return task.model_copy(update={"trade_intent": intent}), None

    @staticmethod
    def select(waiting: dict, message: str) -> dict:
        draft = dict(waiting["draft"])
        if waiting["field"] == "package_id":
            match = re.fullmatch(r"\s*(?:套餐\s*)?(\d+)\s*", message)
            if not match or int(match.group(1)) <= 0:
                raise HTTPException(422, "请选择明确的套餐ID。")
            selected = int(match.group(1))
            allowed = {item["value"] for item in waiting["choices"]}
            if allowed and selected not in allowed:
                raise HTTPException(422, "所选套餐不在当前推荐候选中。")
            draft["package_id"] = selected
        else:
            choice = next((item for item in waiting["choices"] if item["message"] == message.strip()), None)
            if not choice:
                raise HTTPException(422, "请选择当前提供的方案编号。")
            selected = choice["intent"]
            if draft.get("package_id") and selected.get("package_id") and draft["package_id"] != selected["package_id"]:
                raise HTTPException(409, "购买方案与已选套餐不一致。")
            for key in ("trade_type", "activity_id", "package_id", "group_mode", "team_id"):
                if selected.get(key) is not None:
                    draft[key] = selected[key]
        return TradeIntent.model_validate(draft).model_dump()

    @staticmethod
    def thread_id(user_id: str, run_id: str, task_id: str) -> str:
        return hashlib.sha256(f"{user_id}:{run_id}:{task_id}".encode()).hexdigest()[:32]

    def start(self, task: AgentTask, *, user_id: str, authorization: str, session_id: str, run_id: str) -> dict:
        child_id = self.thread_id(user_id, run_id, task.id)
        try:
            return self.service.view(user_id, child_id)
        except HTTPException as exc:
            if exc.status_code != 404:
                raise
        return self.service.turn(user_id=user_id, authorization=authorization, session_id=session_id,
            thread_id=child_id, request_id="start-" + child_id, message=task.objective,
            initial_intent=task.trade_intent.model_dump() if task.trade_intent else None)

    def resume(self, child: dict, *, user_id: str, authorization: str, session_id: str, request_id: str, reply: dict) -> dict:
        current = self.service.view(user_id, child["thread_id"])
        if current["status"] in {"completed", "failed", "rejected", "expired", "stale", "clarification"}:
            return current
        return self.service.turn(user_id=user_id, authorization=authorization, session_id=session_id,
            thread_id=child["thread_id"], request_id=hashlib.sha256(request_id.encode()).hexdigest(),
            message=reply.get("message", ""), approved=reply.get("approved"), confirmation_id=reply.get("confirmation_id"),
            recover=reply.get("recover", False))

    @staticmethod
    def result(task: AgentTask, child: dict) -> TaskResult:
        status = child["status"]
        mapped = ("succeeded" if status == "completed" else "waiting_input" if status in {"awaiting_parameters", "awaiting_confirmation"}
                  else "waiting_external" if status in {"awaiting_result", "uncertain", "unauthorized", "running"} else "failed")
        payload = {"trade": child, "response": child["response"]}
        return TaskResult(task_id=task.id, agent="trade", status=mapped, attempts=1,
                          payload=payload, artifact=TaskArtifact.from_payload(payload))

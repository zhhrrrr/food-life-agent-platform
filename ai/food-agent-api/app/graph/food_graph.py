"""Persistent control graph around a bounded DAG and confirmed Trade children."""
from __future__ import annotations

from collections.abc import Callable
from typing import TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import interrupt

from app.memory.conversation import select_conversation
from app.memory.entities import LABELS, explicit_ids
from app.memory.long_term_memory import LongTermMemoryService
from app.memory.session_memory import SessionTurn
from app.orchestration.composer import compose_execution_result
from app.orchestration.executor import (
    ReadTaskExecutor,
    _execution_status,
    _validate_completed_tasks_unchanged,
)
from app.orchestration.models import DagExecutionResult, ExecutionPlan, TaskResult
from app.orchestration.validator import validate_plan, validate_replan


class FoodAgentState(TypedDict, total=False):
    user_id: str
    session_id: str
    run_id: str
    message: str
    session_history: list
    plan: dict
    results: dict
    resolved_intents: dict
    memory_context: dict
    waiting: dict
    phase: str
    replan_count: int
    last_request_id: str
    output: dict
    resolved_entities: dict
    reference_source: str
    context_expansion_reason: str


class FoodAgentRuntime(TypedDict):
    authorization: str
    request_id: str
    lease_check: Callable[[], None]
    paused: Callable[[], bool]
    emit: Callable[[str, dict], None]


def execution_view(state: dict) -> dict:
    plan = ExecutionPlan.model_validate(state["plan"])
    results = [TaskResult.model_validate(state["results"][t.id]) if t.id in state.get("results", {})
               else TaskResult(task_id=t.id, agent=t.agent, status="pending") for t in plan.tasks]
    execution = DagExecutionResult(plan=plan, task_results=results, status=_execution_status(plan, results),
                                   replan_count=state.get("replan_count", 0))
    return compose_execution_result(execution)


def _session_history(state: dict) -> list[SessionTurn]:
    return [item if isinstance(item, SessionTurn) else SessionTurn.model_validate(item)
            for item in state.get("session_history", [])]


def build_food_graph(settings, checkpointer, *, planner, registry, adapter):
    executor = ReadTaskExecutor(registry=registry, max_parallel_tasks=settings.orchestration_max_parallel_tasks)

    def plan_node(state, runtime: Runtime[FoodAgentRuntime]):
        message = state["message"]
        selection = select_conversation(message, _session_history(state))
        while selection.clarification:
            reply = interrupt({"type": "reference", "message": selection.clarification})
            message = state["message"] + "\n补充：" + reply.get("message", "")
            selection = select_conversation(message, _session_history(state))
        planning_message = selection.resolve_message(message)
        plan = planner.plan(planning_message, history=selection.history).model_copy(update={"run_id": state["run_id"]})
        tasks = []
        for task in plan.tasks:
            objective = task.objective
            intent = task.trade_intent
            applicable = {k: v for k, v in selection.resolved.items()
                          if task.agent == "trade" or (k in {"shop", "package"} and task.agent == "recommend")
                          or (k == "order" and task.agent == "support")}
            for kind, identifier in applicable.items():
                # Planner output is not allowed to replace a resolved user object.
                task_ids = explicit_ids(objective)
                if kind in task_ids and task_ids[kind] != identifier:
                    objective = planning_message
                if kind not in explicit_ids(objective):
                    objective += f"\n{LABELS[kind]}ID：{identifier}"
                field = kind + "_id"
                if intent is not None and field in type(intent).model_fields:
                    intent = intent.model_copy(update={field: identifier if kind == "team" else int(identifier)})
            tasks.append(task.model_copy(update={"objective": objective, "trade_intent": intent}))
        plan = plan.model_copy(update={"tasks": tasks})
        validate_plan(plan, max_tasks=settings.orchestration_max_tasks, max_depth=settings.orchestration_max_depth)
        if any(registry[t.capability].execution == "workflow" for t in plan.tasks) and not runtime.context["authorization"]:
            from fastapi import HTTPException
            raise HTTPException(401, "交易操作需要登录。")
        runtime.context["emit"]("plan_ready", {"tasks": [{"id": t.id, "agent": t.agent, "capability": t.capability, "depends_on": t.depends_on} for t in plan.tasks]})
        return {"plan": plan.model_dump(), "results": {}, "resolved_intents": {}, "replan_count": 0,
                "memory_context": {}, "waiting": {}, "phase": "more",
                "session_history": [t.model_dump() for t in selection.history],
                "resolved_entities": selection.resolved, "reference_source": selection.source_turn_id,
                "context_expansion_reason": selection.expansion_reason}

    def memory_node(state, runtime: Runtime[FoodAgentRuntime]):
        plan = ExecutionPlan.model_validate(state["plan"])
        context = LongTermMemoryService(settings).context_for((t.agent for t in plan.tasks), state["message"],
                         state["user_id"], runtime.context["authorization"])
        return {"memory_context": context}

    def schedule(state, runtime: Runtime[FoodAgentRuntime]):
        ctx = runtime.context
        ctx["lease_check"]()
        if ctx["paused"]():
            return {"waiting": {"type": "paused", "message": "协作任务已暂停，可恢复原运行。"}, "phase": "wait"}
        plan = ExecutionPlan.model_validate(state["plan"])
        results = {k: TaskResult.model_validate(v) for k, v in state.get("results", {}).items()}
        for task in plan.tasks:
            if task.id not in results and any(d in results and results[d].status in {"failed", "skipped"} for d in task.depends_on):
                results[task.id] = TaskResult(task_id=task.id, agent=task.agent, status="skipped", error="required upstream task did not succeed")
        ready = [t for t in plan.tasks if t.id not in results and all(d in results and results[d].status == "succeeded" for d in t.depends_on)]
        reads = [t for t in ready if registry[t.capability].execution == "read"]
        waiting, prepared = None, []
        for task in reads[:settings.orchestration_max_parallel_tasks]:
            if task.capability == "trade.advise":
                task, selection = adapter.prepare(task, results, state.get("resolved_intents", {}))
                if selection:
                    waiting = selection
                    continue
            prepared.append(task)
        if prepared:
            for task in prepared:
                ctx["emit"]("task_started", {"task_id": task.id, "agent": task.agent})
            wave = executor.execute_wave(prepared, results, authorization=ctx["authorization"],
                    session_history=_session_history(state), memory_context_by_agent=state.get("memory_context", {}))
            results.update(wave)
            for result in wave.values():
                ctx["emit"]("task_finished", {"task_id": result.task_id, "status": result.status})
            return {"results": {k: v.model_dump() for k, v in results.items()}, "phase": "more", "waiting": {}}
        if waiting:
            return {"results": {k: v.model_dump() for k, v in results.items()}, "waiting": waiting, "phase": "wait"}
        workflows = [t for t in ready if registry[t.capability].execution == "workflow"]
        if workflows:
            task, selection = adapter.prepare(workflows[0], results, state.get("resolved_intents", {}))
            if selection:
                return {"results": {k: v.model_dump() for k, v in results.items()}, "waiting": selection, "phase": "wait"}
            ctx["lease_check"]()
            child = adapter.start(task, user_id=state["user_id"], authorization=ctx["authorization"], session_id=state["session_id"], run_id=state["run_id"])
            results[task.id] = adapter.result(task, child)
            ctx["emit"]("trade_state", {"task_id": task.id, "status": child["status"]})
            return {"results": {k: v.model_dump() for k, v in results.items()}, "phase": "more", "waiting": {}}
        pending = next((r for r in results.values() if r.status in {"waiting_input", "waiting_external"}), None)
        if pending:
            child = pending.payload["trade"]
            return {"results": {k: v.model_dump() for k, v in results.items()}, "waiting": {"type": "trade", "task_id": pending.task_id,
                    "trade": child, "message": child["response"]}, "phase": "wait"}
        # Read replanning is bounded. It cannot add or replace an active transaction.
        failures = [t for t in plan.tasks if t.required and results.get(t.id) and results[t.id].status in {"failed", "skipped"}]
        if failures and not any(registry[t.capability].execution != "read" for t in plan.tasks) and state.get("replan_count", 0) < settings.orchestration_max_replans:
            revised = planner.replan(plan, list(results.values()))
            if revised is not None:
                validate_replan(plan, revised)
                validate_plan(revised, max_tasks=settings.orchestration_max_tasks,
                              max_depth=settings.orchestration_max_depth)
                if any(registry[t.capability].execution != "read" for t in revised.tasks):
                    raise ValueError("read-only recovery cannot add a transaction")
                _validate_completed_tasks_unchanged(plan, revised, results)
                results = {k: r for k, r in results.items() if r.status == "succeeded"}
                ctx["emit"]("replanned", {"revision": revised.revision})
                return {"plan": revised.model_dump(), "results": {k: v.model_dump() for k, v in results.items()},
                        "replan_count": state.get("replan_count", 0) + 1, "phase": "more"}
        return {"results": {k: v.model_dump() for k, v in results.items()}, "waiting": {}, "phase": "end"}

    def wait_node(state, runtime: Runtime[FoodAgentRuntime]):
        waiting = state["waiting"]
        reply = interrupt(waiting)
        resumed = {"last_request_id": runtime.context["request_id"], "waiting": {}, "phase": "more"}
        if waiting["type"] == "paused":
            return resumed
        if waiting["type"] == "selection":
            resolved = dict(state.get("resolved_intents", {}))
            resolved[waiting["task_id"]] = adapter.select(waiting, reply.get("message", ""))
            return {**resumed, "resolved_intents": resolved}
        task = next(t for t in ExecutionPlan.model_validate(state["plan"]).tasks if t.id == waiting["task_id"])
        child = adapter.resume(waiting["trade"], user_id=state["user_id"], authorization=runtime.context["authorization"],
                 session_id=state["session_id"], request_id=runtime.context["request_id"], reply=reply)
        results = dict(state["results"])
        results[task.id] = adapter.result(task, child).model_dump()
        runtime.context["emit"]("trade_state", {"task_id": task.id, "status": child["status"]})
        return {**resumed, "results": results}

    def compose(state):
        return {"output": execution_view(state)}

    graph = StateGraph(FoodAgentState, context_schema=FoodAgentRuntime)
    for name, node in [("orchestrator", plan_node), ("memory", memory_node), ("schedule", schedule), ("wait", wait_node), ("compose_response", compose)]:
        graph.add_node(name, node)
    graph.add_edge(START, "orchestrator")
    graph.add_edge("orchestrator", "memory")
    graph.add_edge("memory", "schedule")
    graph.add_conditional_edges("schedule", lambda s: {"more": "schedule", "wait": "wait", "end": "compose_response"}[s["phase"]], ["schedule", "wait", "compose_response"])
    graph.add_edge("wait", "schedule")
    graph.add_edge("compose_response", END)
    return graph.compile(checkpointer=checkpointer)


def run_food_agent(message: str, authorization: str = "", session_history: list | None = None,
                   session_id: str = "", user_id: str = "", request_id: str | None = None, **kwargs) -> dict:
    from uuid import uuid4

    from app.orchestration.service import get_orchestration_service
    return get_orchestration_service().turn(message=message, authorization=authorization,
             session_history=session_history or [], session_id=session_id or uuid4().hex, user_id=user_id,
             request_id=request_id or uuid4().hex, **kwargs)

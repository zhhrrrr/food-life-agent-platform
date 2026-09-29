from __future__ import annotations

from collections import Counter, deque

from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.registry import resolve_capability


class PlanValidationError(ValueError):
    pass


def validate_plan(
    plan: ExecutionPlan,
    *,
    max_tasks: int = 12,
    max_depth: int = 6,
) -> ExecutionPlan:
    if len(plan.tasks) > max_tasks:
        raise PlanValidationError(f"plan has more than {max_tasks} tasks")

    task_by_id = {task.id: task for task in plan.tasks}
    if len(task_by_id) != len(plan.tasks):
        raise PlanValidationError("task ids must be unique")

    general_tasks = [task for task in plan.tasks if task.agent == "general"]
    if general_tasks and len(plan.tasks) != 1:
        raise PlanValidationError("GeneralAgent must run alone")
    repeated_agents = [
        agent
        for agent, count in Counter(task.agent for task in plan.tasks).items()
        if count > 2
    ]
    if repeated_agents:
        raise PlanValidationError(
            "an agent can appear at most twice: " + ", ".join(repeated_agents)
        )

    children: dict[str, list[str]] = {task.id: [] for task in plan.tasks}
    indegree: dict[str, int] = {task.id: 0 for task in plan.tasks}
    depth: dict[str, int] = {task.id: 1 for task in plan.tasks}

    for task in plan.tasks:
        spec = resolve_capability(task.capability)
        if spec.agent != task.agent:
            raise PlanValidationError(
                f"capability {task.capability} does not belong to {task.agent}"
            )
        for dependency in task.depends_on:
            if dependency not in task_by_id:
                raise PlanValidationError(
                    f"task {task.id} depends on missing task {dependency}"
                )
            children[dependency].append(task.id)
            indegree[task.id] += 1

    ready = deque(task_id for task_id, count in indegree.items() if count == 0)
    visited = 0
    while ready:
        task_id = ready.popleft()
        visited += 1
        for child_id in children[task_id]:
            depth[child_id] = max(depth[child_id], depth[task_id] + 1)
            indegree[child_id] -= 1
            if indegree[child_id] == 0:
                ready.append(child_id)

    if visited != len(plan.tasks):
        raise PlanValidationError("plan contains a dependency cycle")
    if max(depth.values(), default=0) > max_depth:
        raise PlanValidationError(f"plan depth exceeds {max_depth}")

    for task in plan.tasks:
        if task.capability == "recommend.rerank" and not task.depends_on:
            raise PlanValidationError("recommend.rerank requires upstream evidence")
        if task.capability == "support.explain":
            trade_tasks = {item.id for item in plan.tasks if item.agent == "trade"}
            if trade_tasks and not _has_upstream(task.id, trade_tasks, task_by_id):
                raise PlanValidationError(
                    "support.explain must depend on trade facts when TradeAgent is present"
                )
    return plan


def validate_replan(previous: ExecutionPlan, revised: ExecutionPlan) -> ExecutionPlan:
    if revised.run_id != previous.run_id:
        raise PlanValidationError("replan cannot change run_id")
    if revised.revision != previous.revision + 1:
        raise PlanValidationError("replan revision must increase by one")
    return revised


def _has_upstream(
    task_id: str,
    candidates: set[str],
    task_by_id: dict[str, AgentTask],
) -> bool:
    visited: set[str] = set()
    pending = list(task_by_id[task_id].depends_on)
    while pending:
        dependency = pending.pop()
        if dependency in candidates:
            return True
        if dependency in visited:
            continue
        visited.add(dependency)
        pending.extend(task_by_id[dependency].depends_on)
    return False

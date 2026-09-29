from __future__ import annotations

from collections import Counter, deque

from app.orchestration.models import ExecutionPlan
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

    return plan


def validate_replan(previous: ExecutionPlan, revised: ExecutionPlan) -> ExecutionPlan:
    if revised.run_id != previous.run_id:
        raise PlanValidationError("replan cannot change run_id")
    if revised.revision != previous.revision + 1:
        raise PlanValidationError("replan revision must increase by one")
    return revised

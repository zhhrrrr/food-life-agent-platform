from __future__ import annotations

import logging
from collections.abc import Mapping
from concurrent.futures import FIRST_COMPLETED, Future, wait
from functools import partial
from time import monotonic

from langsmith import traceable
from app.core.langsmith import redact, trace_inputs

from app.memory.entities import displayed_entity_groups
from app.orchestration.models import (
    AgentTask,
    ExecutionPlan,
    TaskArtifact,
    TaskResult,
)
from app.orchestration.registry import CAPABILITY_REGISTRY, CapabilitySpec
from app.tools.runtime import get_tool_pool

logger = logging.getLogger("food_agent.orchestration.executor")


class ReadTaskExecutor:
    """Execute read workers; the persistent StateGraph owns scheduling and replans."""
    def __init__(
        self,
        *,
        registry: Mapping[str, CapabilitySpec] | None = None,
        max_parallel_tasks: int = 3,
    ) -> None:
        self.registry = registry if registry is not None else CAPABILITY_REGISTRY
        self.max_parallel_tasks = max(1, max_parallel_tasks)


    def execute_wave(
        self,
        tasks: list[AgentTask],
        completed: dict[str, TaskResult],
        *,
        authorization: str,
        session_history: list[object],
        memory_context_by_agent: Mapping[str, dict],
    ) -> dict[str, TaskResult]:
        pool = get_tool_pool(self.max_parallel_tasks, namespace="agent")
        future_by_task: dict[Future[TaskResult], AgentTask] = {}
        deadline_by_future: dict[Future[TaskResult], float] = {}
        wave_results: dict[str, TaskResult] = {}
        for task in tasks:
            deadline = monotonic() + task.timeout_seconds
            try:
                future = pool.submit(partial(self._execute_task, task, completed, authorization,
                                             session_history, memory_context_by_agent, deadline))
            except RuntimeError as exc:
                wave_results[task.id] = TaskResult(task_id=task.id, agent=task.agent,
                    status="failed", attempts=0, error=f"agent worker unavailable: {exc}")
                continue
            future_by_task[future] = task
            deadline_by_future[future] = deadline
        pending = set(future_by_task)
        while pending:
            now = monotonic()
            expired = {
                future
                for future in pending
                if deadline_by_future[future] <= now and not future.done()
            }
            for future in expired:
                task = future_by_task[future]
                future.cancel()
                wave_results[task.id] = TaskResult(
                    task_id=task.id,
                    agent=task.agent,
                    status="failed",
                    attempts=1,
                    error=f"task timed out after {task.timeout_seconds:g}s",
                )
            pending -= expired
            if not pending:
                break

            next_deadline = min(deadline_by_future[future] for future in pending)
            done, _ = wait(
                pending,
                timeout=max(0.0, next_deadline - monotonic()),
                return_when=FIRST_COMPLETED,
            )
            for future in done:
                pending.remove(future)
                task = future_by_task[future]
                try:
                    wave_results[task.id] = future.result()
                except Exception as exc:  # noqa: BLE001 - isolate worker failures
                    wave_results[task.id] = TaskResult(
                        task_id=task.id,
                        agent=task.agent,
                        status="failed",
                        attempts=task.max_attempts,
                        error=f"{type(exc).__name__}: {exc}",
                    )
        return wave_results

    @traceable(name="agent.task", process_inputs=trace_inputs, process_outputs=redact)
    def _execute_task(
        self,
        task: AgentTask,
        completed: dict[str, TaskResult],
        authorization: str,
        session_history: list[object],
        memory_context_by_agent: Mapping[str, dict],
        deadline: float,
    ) -> TaskResult:
        spec = self.registry.get(task.capability)
        if spec is None or spec.agent != task.agent:
            raise ValueError(f"unregistered capability: {task.capability}")
        if spec.execution != "read":
            raise ValueError("controlled workflows cannot run in the retrying read executor")
        state = {
            "message": task.objective,
            "memory_context": memory_context_by_agent.get(task.agent, {}),
            "authorization": authorization,
            "session_history": session_history,
            "trade_intent": task.trade_intent.model_dump() if task.trade_intent else {},
            "upstream_artifacts": {
                dependency: completed[dependency].artifact.model_dump(mode="json")
                for dependency in task.depends_on
                if dependency in completed
            },
        }
        last_error = ""
        attempts = 0
        for attempt in range(1, task.max_attempts + 1):
            if monotonic() >= deadline:
                last_error = f"task timed out after {task.timeout_seconds:g}s"
                break
            attempts = attempt
            try:
                payload = spec.handler(state)
                if monotonic() >= deadline:
                    last_error = f"task timed out after {task.timeout_seconds:g}s"
                    break
                payload = {**payload, "entity_groups": [g.model_dump() for g in displayed_entity_groups(payload, task.id)]}
                return TaskResult(
                    task_id=task.id,
                    agent=task.agent,
                    status="succeeded",
                    attempts=attempt,
                    payload=payload,
                    artifact=TaskArtifact.from_payload(payload),
                )
            except Exception as exc:  # noqa: BLE001 - retry registered worker boundary
                last_error = f"{type(exc).__name__}: {exc}"
                logger.warning(
                    "agent_task_failed task_id=%s agent=%s attempt=%s error=%s",
                    task.id,
                    task.agent,
                    attempt,
                    type(exc).__name__,
                )
        return TaskResult(
            task_id=task.id,
            agent=task.agent,
            status="failed",
            attempts=attempts,
            error=last_error,
        )


def _validate_completed_tasks_unchanged(
    previous: ExecutionPlan,
    revised: ExecutionPlan,
    results: dict[str, TaskResult],
) -> None:
    previous_tasks = {task.id: task for task in previous.tasks}
    revised_tasks = {task.id: task for task in revised.tasks}
    for task_id, result in results.items():
        if result.status != "succeeded":
            continue
        if task_id not in revised_tasks or revised_tasks[task_id] != previous_tasks[task_id]:
            raise ValueError("replan cannot modify a completed task")


def _execution_status(
    plan: ExecutionPlan,
    results: list[TaskResult],
) -> str:
    if any(result.status == "waiting_input" for result in results):
        return "waiting_input"
    if any(result.status == "waiting_external" for result in results):
        return "waiting_external"
    failed_required = {
        task.id
        for task in plan.tasks
        if task.required
        and any(
            result.task_id == task.id and result.status != "succeeded"
            for result in results
        )
    }
    succeeded = any(result.status == "succeeded" for result in results)
    if failed_required:
        return "partial" if succeeded else "failed"
    return "completed"

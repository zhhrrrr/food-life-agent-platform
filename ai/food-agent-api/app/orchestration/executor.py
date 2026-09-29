from __future__ import annotations

import json
import logging
from collections.abc import Callable, Mapping
from concurrent.futures import Future, ThreadPoolExecutor, wait

from app.orchestration.models import (
    AgentTask,
    DagExecutionResult,
    ExecutionPlan,
    TaskResult,
)
from app.orchestration.registry import CAPABILITY_REGISTRY, CapabilitySpec
from app.orchestration.validator import validate_plan, validate_replan

logger = logging.getLogger("food_agent.orchestration.executor")
Replanner = Callable[[ExecutionPlan, list[TaskResult]], ExecutionPlan | None]


class DynamicDagExecutor:
    def __init__(
        self,
        *,
        registry: Mapping[str, CapabilitySpec] | None = None,
        max_parallel_tasks: int = 3,
        max_replans: int = 1,
    ) -> None:
        self.registry = registry or CAPABILITY_REGISTRY
        self.max_parallel_tasks = max(1, max_parallel_tasks)
        self.max_replans = max(0, max_replans)

    def execute(
        self,
        plan: ExecutionPlan,
        *,
        authorization: str = "",
        conversation_history: list[object] | None = None,
        replanner: Replanner | None = None,
    ) -> DagExecutionResult:
        current_plan = validate_plan(plan)
        results: dict[str, TaskResult] = {}
        replan_count = 0

        while True:
            self._execute_pending(
                current_plan,
                results,
                authorization=authorization,
                conversation_history=conversation_history or [],
            )
            required_failures = [
                task
                for task in current_plan.tasks
                if task.required
                and results.get(task.id)
                and results[task.id].status in {"failed", "skipped"}
            ]
            if not required_failures or replanner is None or replan_count >= self.max_replans:
                break

            revised = replanner(current_plan, list(results.values()))
            if revised is None:
                break
            validate_replan(current_plan, revised)
            validate_plan(revised)
            _validate_completed_tasks_unchanged(current_plan, revised, results)
            revised_task_ids = {task.id for task in revised.tasks}
            results = {
                task_id: result
                for task_id, result in results.items()
                if task_id in revised_task_ids and result.status == "succeeded"
            }
            current_plan = revised
            replan_count += 1

        ordered_results = [
            results.get(task.id)
            or TaskResult(
                task_id=task.id,
                agent=task.agent,
                status="skipped",
                error="task was not scheduled",
            )
            for task in current_plan.tasks
        ]
        return DagExecutionResult(
            plan=current_plan,
            task_results=ordered_results,
            status=_execution_status(current_plan, ordered_results),
            replan_count=replan_count,
        )

    def _execute_pending(
        self,
        plan: ExecutionPlan,
        results: dict[str, TaskResult],
        *,
        authorization: str,
        conversation_history: list[object],
    ) -> None:
        task_by_id = {task.id: task for task in plan.tasks}
        while len(results) < len(task_by_id):
            progress = False
            for task in plan.tasks:
                if task.id in results:
                    continue
                failed_dependencies = [
                    dependency
                    for dependency in task.depends_on
                    if dependency in results
                    and results[dependency].status != "succeeded"
                ]
                if failed_dependencies:
                    results[task.id] = TaskResult(
                        task_id=task.id,
                        agent=task.agent,
                        status="skipped",
                        error="dependency failed: " + ", ".join(failed_dependencies),
                    )
                    progress = True

            ready = [
                task
                for task in plan.tasks
                if task.id not in results
                and all(
                    dependency in results
                    and results[dependency].status == "succeeded"
                    for dependency in task.depends_on
                )
            ]
            if ready:
                wave = ready[: self.max_parallel_tasks]
                results.update(
                    self._execute_wave(
                        wave,
                        results,
                        authorization=authorization,
                        conversation_history=conversation_history,
                    )
                )
                progress = True
            if not progress:
                raise RuntimeError("DAG scheduler reached a non-progressing state")

    def _execute_wave(
        self,
        tasks: list[AgentTask],
        completed: dict[str, TaskResult],
        *,
        authorization: str,
        conversation_history: list[object],
    ) -> dict[str, TaskResult]:
        executor = ThreadPoolExecutor(max_workers=min(len(tasks), self.max_parallel_tasks))
        future_by_task: dict[Future[TaskResult], AgentTask] = {
            executor.submit(
                self._execute_task,
                task,
                completed,
                authorization,
                conversation_history,
            ): task
            for task in tasks
        }
        timeout = max(task.timeout_seconds for task in tasks)
        done, pending = wait(future_by_task, timeout=timeout)
        wave_results: dict[str, TaskResult] = {}
        for future in done:
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
        for future in pending:
            task = future_by_task[future]
            future.cancel()
            wave_results[task.id] = TaskResult(
                task_id=task.id,
                agent=task.agent,
                status="failed",
                attempts=1,
                error=f"task timed out after {task.timeout_seconds:g}s",
            )
        executor.shutdown(wait=False, cancel_futures=True)
        return wave_results

    def _execute_task(
        self,
        task: AgentTask,
        completed: dict[str, TaskResult],
        authorization: str,
        conversation_history: list[object],
    ) -> TaskResult:
        spec = self.registry.get(task.capability)
        if spec is None or spec.agent != task.agent:
            raise ValueError(f"unregistered capability: {task.capability}")
        state = {
            "message": _worker_message(task, completed),
            "authorization": authorization,
            "conversation_history": conversation_history,
        }
        last_error = ""
        for attempt in range(1, task.max_attempts + 1):
            try:
                payload = spec.handler(state)
                return TaskResult(
                    task_id=task.id,
                    agent=task.agent,
                    status="succeeded",
                    attempts=attempt,
                    payload=payload,
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
            attempts=task.max_attempts,
            error=last_error,
        )


def _worker_message(task: AgentTask, completed: dict[str, TaskResult]) -> str:
    if not task.depends_on:
        return task.objective
    upstream = {
        dependency: completed[dependency].payload
        for dependency in task.depends_on
        if dependency in completed
    }
    serialized = json.dumps(upstream, ensure_ascii=False, default=str)[:6000]
    return (
        f"{task.objective}\n\n"
        "已验证的上游任务结果如下。仅使用其中明确存在的 ID、事实和引用，"
        f"不要补造缺失信息：\n{serialized}"
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

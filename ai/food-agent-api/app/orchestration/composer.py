from __future__ import annotations

from app.memory.entities import displayed_entity_groups
from app.orchestration.models import DagExecutionResult, TaskResult

AGENT_LABELS = {
    "recommend": "推荐结果",
    "support": "规则说明",
    "trade": "实时查询",
    "general": "答复",
}


def compose_execution_result(execution: DagExecutionResult) -> dict[str, object]:
    successful = [
        result for result in execution.task_results if result.status == "succeeded"
    ]
    agents = _plan_agents(execution)
    references = _deduplicate_references(successful)

    if len(successful) == 1:
        payload = successful[0].payload
        response = str(payload.get("response") or "任务已完成，但没有可展示的回答。")
        rag_mode = str(payload.get("rag_mode") or "")
    elif successful:
        sections = []
        for result in successful:
            response_part = str(result.payload.get("response") or "").strip()
            if response_part:
                sections.append(f"【{AGENT_LABELS[result.agent]}】\n{response_part}")
        response = "\n\n".join(sections) or "相关任务已经完成。"
        rag_mode = "multi_agent_dynamic_dag"
    else:
        response = "本次任务暂时无法完成，请稍后重试。"
        rag_mode = "multi_agent_failed"

    if execution.status == "partial":
        response += "\n\n部分子任务未成功，以上仅包含已确认的结果。"

    intent = agents[0] if len(agents) == 1 else "multi"
    return {
        "run_id": execution.plan.run_id,
        "plan_revision": execution.plan.revision,
        "workflow": _workflow_name(agents),
        "intent": intent if agents else "general",
        "agent": intent if agents else "general",
        "agents": agents,
        "response": response,
        "rag_mode": rag_mode,
        "references": references,
        "task_results": [result.model_dump() for result in execution.task_results],
        "execution_status": execution.status,
        "replan_count": execution.replan_count,
        "entity_groups": [g.model_dump() for result in successful for g in displayed_entity_groups(result.payload, result.task_id)],
        "session_constraints": next((result.payload["session_constraints"] for result in reversed(successful)
                                      if result.agent == "recommend" and result.payload.get("session_constraints")), {}),
    }


def _plan_agents(execution: DagExecutionResult) -> list[str]:
    agents: list[str] = []
    for task in execution.plan.tasks:
        if task.agent not in agents:
            agents.append(task.agent)
    return agents


def _deduplicate_references(results: list[TaskResult]) -> list[dict[str, object]]:
    references: list[dict[str, object]] = []
    seen: set[tuple[str, str]] = set()
    for result in results:
        raw_references = result.payload.get("references") or []
        if not isinstance(raw_references, list):
            continue
        for reference in raw_references:
            if not isinstance(reference, dict):
                continue
            key = (str(reference.get("id") or ""), str(reference.get("source") or ""))
            if key in seen:
                continue
            seen.add(key)
            references.append(reference)
    return references


def _workflow_name(agents: list[str]) -> str:
    if len(agents) <= 1:
        return f"{agents[0] if agents else 'general'}_only"
    return "dynamic_" + "_".join(agents)

from __future__ import annotations

from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from app.core.config import get_settings
from app.memory.long_term_memory import LongTermMemoryService
from app.memory.session_memory import SessionTurn
from app.orchestration.composer import compose_execution_result
from app.orchestration.executor import DynamicDagExecutor
from app.orchestration.models import DagExecutionResult, ExecutionPlan
from app.orchestration.planner import DynamicPlanner


class FoodAgentState(TypedDict, total=False):
    message: str
    authorization: str
    session_history: list[SessionTurn]
    session_id: str
    user_id: str
    plan: dict[str, object]
    execution: dict[str, object]
    run_id: str
    plan_revision: int
    workflow: str
    execution_status: str
    replan_count: int
    intent: str
    agent: str
    agents: list[str]
    response: str
    rag_mode: str
    router_confidence: float
    router_reason: str
    router_source: str
    references: list[dict[str, object]]
    task_results: list[dict[str, object]]


def orchestrator_node(state: FoodAgentState) -> dict[str, object]:
    plan = DynamicPlanner().plan(
        state["message"],
        history=state.get("session_history", []),
    )
    return {
        "plan": plan.model_dump(),
        "run_id": plan.run_id,
        "plan_revision": plan.revision,
        "router_confidence": plan.confidence,
        "router_reason": plan.reason,
        "router_source": plan.source,
    }


def execute_plan_node(state: FoodAgentState) -> dict[str, object]:
    settings = get_settings()
    plan = ExecutionPlan.model_validate(state["plan"])
    memory_context_by_agent = LongTermMemoryService(settings).context_for(
        (task.agent for task in plan.tasks),
        state["message"],
        state.get("user_id", ""),
        state.get("authorization", ""),
    )
    planner = DynamicPlanner(settings=settings)
    executor = DynamicDagExecutor(
        max_parallel_tasks=settings.orchestration_max_parallel_tasks,
        max_replans=settings.orchestration_max_replans,
    )
    execution = executor.execute(
        plan,
        authorization=state.get("authorization", ""),
        session_history=state.get("session_history", []),
        memory_context_by_agent=memory_context_by_agent,
        replanner=planner.replan,
    )
    return {"execution": execution.model_dump()}


def compose_response_node(state: FoodAgentState) -> dict[str, object]:
    execution = DagExecutionResult.model_validate(state["execution"])
    return compose_execution_result(execution)


def build_food_graph():
    graph = StateGraph(FoodAgentState)
    graph.add_node("orchestrator", orchestrator_node)
    graph.add_node("execute_plan", execute_plan_node)
    graph.add_node("compose_response", compose_response_node)
    graph.add_edge(START, "orchestrator")
    graph.add_edge("orchestrator", "execute_plan")
    graph.add_edge("execute_plan", "compose_response")
    graph.add_edge("compose_response", END)
    return graph.compile()


food_graph = build_food_graph()


def run_food_agent(
    message: str,
    authorization: str = "",
    session_history: list[SessionTurn] | None = None,
    session_id: str = "",
    user_id: str = "",
) -> FoodAgentState:
    initial_state: FoodAgentState = {
        "message": message,
        "authorization": authorization,
        "session_history": session_history or [],
        "session_id": session_id,
        "user_id": user_id,
    }
    return food_graph.invoke(initial_state)

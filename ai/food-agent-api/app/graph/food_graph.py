from typing import Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.agents.trade_agent import trade_agent_node
from app.graph.router import route_intent

AgentName = Literal["recommend", "trade", "support"]


class FoodAgentState(TypedDict):
    message: str
    intent: str
    agent: str
    response: str
    rag_mode: str
    query: str
    rewritten_query: str
    capabilities: list[str]
    router_confidence: float
    router_reason: str
    context: list[dict[str, Any]]
    references: list[dict[str, Any]]


def router_node(state: FoodAgentState) -> dict[str, object]:
    result = route_intent(state["message"])
    return {
        "intent": result.intent,
        "agent": result.intent,
        "router_confidence": result.confidence,
        "router_reason": result.reason,
    }


def route_agent(state: FoodAgentState) -> AgentName:
    return state["agent"]  # type: ignore[return-value]


def build_food_graph():
    graph = StateGraph(FoodAgentState)

    graph.add_node("router", router_node)
    graph.add_node("recommend", recommend_agent_node)
    graph.add_node("trade", trade_agent_node)
    graph.add_node("support", support_agent_node)

    graph.add_edge(START, "router")
    graph.add_conditional_edges(
        "router",
        route_agent,
        {
            "recommend": "recommend",
            "trade": "trade",
            "support": "support",
        },
    )
    graph.add_edge("recommend", END)
    graph.add_edge("trade", END)
    graph.add_edge("support", END)

    return graph.compile()


food_graph = build_food_graph()


def run_food_agent(message: str) -> FoodAgentState:
    initial_state: FoodAgentState = {
        "message": message,
        "intent": "",
        "agent": "",
        "response": "",
        "rag_mode": "",
        "query": "",
        "rewritten_query": "",
        "capabilities": [],
        "router_confidence": 0.0,
        "router_reason": "",
        "context": [],
        "references": [],
    }

    return food_graph.invoke(initial_state)

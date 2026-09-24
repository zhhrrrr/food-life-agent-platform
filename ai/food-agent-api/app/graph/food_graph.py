from typing import Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.agents.trade_agent import trade_agent_node
from app.graph.router import route_intent
from app.memory.conversation_memory import ConversationTurn

AgentName = Literal["recommend", "trade", "support"]


class FoodAgentState(TypedDict):
    message: str
    authorization: str
    intent: str
    agent: str
    response: str
    rag_mode: str
    query: str
    rewritten_query: str
    capabilities: list[str]
    order_status: dict[str, object] | None
    facts: list[dict[str, object]]
    query_statuses: list[dict[str, object]]
    operation_executed: bool
    conversation_history: list[ConversationTurn]
    router_confidence: float
    router_reason: str
    router_source: str
    router_tool: str
    semantic_score: float | None
    semantic_margin: float | None
    context: list[dict[str, Any]]
    references: list[dict[str, Any]]


def router_node(state: FoodAgentState) -> dict[str, object]:
    result = route_intent(
        state["message"],
        conversation_history=state.get("conversation_history", []),
    )
    return {
        "intent": result.intent,
        "agent": result.intent,
        "router_confidence": result.confidence,
        "router_reason": result.reason,
        "router_source": result.source,
        "router_tool": result.tool,
        "semantic_score": result.semantic_score,
        "semantic_margin": result.semantic_margin,
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


def run_food_agent(
    message: str,
    authorization: str = "",
    conversation_history: list[ConversationTurn] | None = None,
) -> FoodAgentState:
    initial_state: FoodAgentState = {
        "message": message,
        "authorization": authorization,
        "intent": "",
        "agent": "",
        "response": "",
        "rag_mode": "",
        "query": "",
        "rewritten_query": "",
        "capabilities": [],
        "order_status": None,
        "facts": [],
        "query_statuses": [],
        "operation_executed": False,
        "conversation_history": conversation_history or [],
        "router_confidence": 0.0,
        "router_reason": "",
        "router_source": "",
        "router_tool": "",
        "semantic_score": None,
        "semantic_margin": None,
        "context": [],
        "references": [],
    }

    return food_graph.invoke(initial_state)

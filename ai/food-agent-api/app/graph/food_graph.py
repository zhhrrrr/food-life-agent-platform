from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.agents.trade_agent import trade_agent_node
from app.graph.router import AgentName, route_intent
from app.memory.conversation_memory import ConversationTurn


class FoodAgentState(TypedDict, total=False):
    message: str
    authorization: str
    intent: AgentName
    agent: AgentName
    response: str
    rag_mode: str
    query: str
    rewritten_query: str
    capabilities: list[str]
    facts: list[dict[str, object]]
    query_statuses: list[dict[str, object]]
    operation_executed: bool
    conversation_history: list[ConversationTurn]
    router_confidence: float
    router_reason: str
    router_source: str
    context: list[dict[str, object]]
    references: list[dict[str, object]]


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
    }


def route_agent(state: FoodAgentState) -> AgentName:
    return state["agent"]


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
        "conversation_history": conversation_history or [],
    }

    return food_graph.invoke(initial_state)

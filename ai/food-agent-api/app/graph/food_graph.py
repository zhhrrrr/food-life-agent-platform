from typing import Any, Literal, TypedDict

from langgraph.graph import END, START, StateGraph

from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.agents.trade_agent import trade_agent_node

AgentName = Literal["recommend", "trade", "support"]


class FoodAgentState(TypedDict):
    message: str
    intent: str
    agent: str
    response: str
    rag_mode: str
    context: list[dict[str, Any]]
    references: list[dict[str, Any]]


TRADE_KEYWORDS = (
    "\u8ba2\u5355",
    "\u4e0b\u5355",
    "\u5e93\u5b58",
    "\u4f18\u60e0\u5238",
    "\u4ef7\u683c",
    "\u62fc\u56e2",
    "\u53d6\u6d88",
)
SUPPORT_KEYWORDS = (
    "\u9000\u6b3e",
    "\u552e\u540e",
    "\u89c4\u5219",
    "\u652f\u4ed8",
    "FAQ",
    "faq",
)


def router_node(state: FoodAgentState) -> dict[str, str]:
    message = state["message"]

    # TODO:
    # Replace this rule-based router with LLM + Pydantic Structured Output.
    if any(keyword in message for keyword in TRADE_KEYWORDS):
        intent: AgentName = "trade"
    elif any(keyword in message for keyword in SUPPORT_KEYWORDS):
        intent = "support"
    else:
        intent = "recommend"

    return {"intent": intent, "agent": intent}


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
        "context": [],
        "references": [],
    }

    return food_graph.invoke(initial_state)

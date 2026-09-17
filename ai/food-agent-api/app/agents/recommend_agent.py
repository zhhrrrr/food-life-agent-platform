from typing import TypedDict

from app.rag.context_builder import build_context_summary, build_references
from app.rag.retriever import retrieve_for_agent


class RecommendAgentInput(TypedDict):
    message: str


def recommend_agent_node(state: RecommendAgentInput) -> dict[str, object]:
    hits = retrieve_for_agent("recommend", state["message"])
    if hits:
        context = build_context_summary(hits)
        references = build_references(hits)
        return {
            "response": (
                "Recommend Agent found related food-life results:\n"
                f"{context}"
            ),
            "references": [reference.model_dump() for reference in references],
        }

    return {
        "response": f"Recommend Agent \u6536\u5230\u8bf7\u6c42\uff1a{state['message']}",
        "references": [],
    }

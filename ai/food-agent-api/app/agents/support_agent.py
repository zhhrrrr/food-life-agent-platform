from typing import TypedDict

from app.rag.context_builder import build_context_summary, build_references
from app.rag.retriever import retrieve_for_agent


class SupportAgentInput(TypedDict):
    message: str


def support_agent_node(state: SupportAgentInput) -> dict[str, object]:
    hits = retrieve_for_agent("support", state["message"])
    if hits:
        context = build_context_summary(hits)
        references = build_references(hits)
        return {
            "response": (
                "Support Agent found related platform rules:\n"
                f"{context}"
            ),
            "references": [reference.model_dump() for reference in references],
        }

    return {
        "response": f"Support Agent \u6536\u5230\u8bf7\u6c42\uff1a{state['message']}",
        "references": [],
    }

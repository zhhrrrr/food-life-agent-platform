from typing import TypedDict

from app.rag.context_builder import build_context_items, build_context_summary, build_references
from app.rag.retriever import RagRetriever


class SupportAgentInput(TypedDict):
    message: str


def support_agent_node(state: SupportAgentInput) -> dict[str, object]:
    retrieval = RagRetriever().retrieve("support", state["message"])
    if retrieval.has_hits:
        context = build_context_summary(retrieval.hits)
        references = build_references(retrieval.hits)
        return {
            "response": (
                "\u6839\u636e\u5e73\u53f0\u89c4\u5219\u68c0\u7d22\u7ed3\u679c\uff0c\u53ef\u4ee5\u53c2\u8003\u4ee5\u4e0b\u4f9d\u636e\uff1a\n"
                f"{context}"
            ),
            "rag_mode": "retrieval_template",
            "context": build_context_items(retrieval.hits),
            "references": [reference.model_dump() for reference in references],
        }

    return {
        "response": (
            "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u76f8\u5173\u5e73\u53f0\u89c4\u5219\u6216 FAQ \u4f9d\u636e\uff0c"
            "\u8bf7\u5148\u5c06\u6b63\u5f0f\u8fd0\u8425 Markdown \u6216\u914d\u7f6e\u6587\u6863\u5bfc\u5165\u89c4\u5219\u7d22\u5f15\u3002"
        ),
        "rag_mode": "retrieval_template",
        "context": [],
        "references": [],
    }

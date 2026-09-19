from typing import TypedDict

from app.rag.context_builder import build_context_items, build_context_summary, build_references
from app.rag.retriever import RagRetriever


class RecommendAgentInput(TypedDict):
    message: str


def recommend_agent_node(state: RecommendAgentInput) -> dict[str, object]:
    retrieval = RagRetriever().retrieve("recommend", state["message"])
    if retrieval.has_hits:
        context = build_context_summary(retrieval.hits)
        references = build_references(retrieval.hits)
        return {
            "response": (
                "\u6839\u636e\u68c0\u7d22\u7ed3\u679c\uff0c\u4e3a\u4f60\u627e\u5230\u4ee5\u4e0b\u5546\u5bb6\u548c\u5957\u9910\u7ebf\u7d22\uff1a\n"
                f"{context}"
            ),
            "rag_mode": "retrieval_template",
            "context": build_context_items(retrieval.hits),
            "references": [reference.model_dump() for reference in references],
        }

    return {
        "response": (
            "\u6682\u672a\u4ece ES \u68c0\u7d22\u5230\u53ef\u7528\u4f9d\u636e\uff0c"
            "\u5148\u5efa\u8bae\u8865\u5145\u95e8\u5e97\u3001\u5957\u9910\u3001\u8bc4\u4ef7\u6216\u6d3b\u52a8\u7d22\u5f15\u540e\u518d\u8fdb\u884c\u63a8\u8350\u3002"
        ),
        "rag_mode": "retrieval_template",
        "context": [],
        "references": [],
    }

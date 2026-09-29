from typing import TypedDict

from app.memory.session_memory import SessionTurn
from app.recommend.chain import RecommendChain


class RecommendAgentInput(TypedDict, total=False):
    message: str
    session_history: list[SessionTurn]


def recommend_agent_node(state: RecommendAgentInput) -> dict[str, object]:
    return RecommendChain().run(
        state["message"],
        session_history=state.get("session_history", []),
    ).model_dump()

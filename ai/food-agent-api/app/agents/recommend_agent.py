from typing import TypedDict

from app.recommend.chain import RecommendChain


class RecommendAgentInput(TypedDict):
    message: str


def recommend_agent_node(state: RecommendAgentInput) -> dict[str, object]:
    return RecommendChain().run(state["message"]).model_dump()

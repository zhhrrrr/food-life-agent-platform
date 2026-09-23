from typing import TypedDict

from app.support.chain import SupportChain


class SupportAgentInput(TypedDict):
    message: str
    authorization: str


def support_agent_node(state: SupportAgentInput) -> dict[str, object]:
    return SupportChain().run(
        state["message"],
        authorization=state.get("authorization", ""),
    ).model_dump()

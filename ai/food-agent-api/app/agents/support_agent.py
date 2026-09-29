from typing import TypedDict

from app.support.chain import SupportChain


class SupportAgentInput(TypedDict, total=False):
    message: str
    upstream_artifacts: dict[str, object]


def support_agent_node(state: SupportAgentInput) -> dict[str, object]:
    return SupportChain().run(
        state["message"],
        upstream_artifacts=state.get("upstream_artifacts", {}),
    ).model_dump()

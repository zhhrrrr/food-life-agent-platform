from typing import TypedDict

from app.memory.session_memory import SessionTurn
from app.support.chain import SupportChain


class SupportAgentInput(TypedDict, total=False):
    message: str
    upstream_artifacts: dict[str, object]
    session_history: list[SessionTurn]
    memory_context: dict


def support_agent_node(state: SupportAgentInput) -> dict[str, object]:
    return SupportChain().run(
        state["message"],
        upstream_artifacts=state.get("upstream_artifacts", {}),
        session_history=state.get("session_history", []),
        memory_context=state.get("memory_context", {}),
    ).model_dump()

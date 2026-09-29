from typing import TypedDict

from app.memory.session_memory import SessionTurn
from app.trade.chain import TradeChain


class TradeAgentInput(TypedDict, total=False):
    message: str
    authorization: str
    upstream_artifacts: dict[str, object]
    session_history: list[SessionTurn]


def trade_agent_node(state: TradeAgentInput) -> dict[str, object]:
    return TradeChain().run(
        state["message"],
        authorization=state.get("authorization", ""),
        upstream_artifacts=state.get("upstream_artifacts", {}),
        session_history=state.get("session_history", []),
    ).model_dump()

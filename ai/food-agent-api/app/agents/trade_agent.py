from typing import TypedDict

from app.trade.chain import TradeChain


class TradeAgentInput(TypedDict):
    message: str
    authorization: str
    upstream_artifacts: dict[str, object]


def trade_agent_node(state: TradeAgentInput) -> dict[str, object]:
    return TradeChain().run(
        state["message"],
        authorization=state.get("authorization", ""),
        upstream_artifacts=state.get("upstream_artifacts", {}),
    ).model_dump()

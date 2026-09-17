from typing import TypedDict


class TradeAgentInput(TypedDict):
    message: str


def trade_agent_node(state: TradeAgentInput) -> dict[str, object]:
    return {
        "response": f"Trade Agent \u6536\u5230\u8bf7\u6c42\uff1a{state['message']}",
        "references": [],
    }

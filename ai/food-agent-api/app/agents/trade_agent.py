from typing import TypedDict

from app.memory.session_memory import SessionTurn
from app.core.config import get_settings
from app.trade.chain import TradeChain, query_results_payload
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient
from app.trade.intent import TradeIntent
from app.trade.purchase import PurchaseReader, purchase_advice_summary


class TradeAgentInput(TypedDict, total=False):
    message: str
    authorization: str
    upstream_artifacts: dict[str, object]
    session_history: list[SessionTurn]
    trade_intent: dict[str, object]


def _purchase_reader() -> PurchaseReader:
    settings = get_settings()
    return PurchaseReader(TradeGatewayClient(settings), settings)


def trade_agent_node(state: TradeAgentInput) -> dict[str, object]:
    if state.get("trade_intent"):
        intent = TradeIntent.model_validate(state["trade_intent"])
        result = _purchase_reader().query(intent, state.get("authorization", ""))
        return query_results_payload([result])
    return TradeChain().run(
        state["message"],
        authorization=state.get("authorization", ""),
        upstream_artifacts=state.get("upstream_artifacts", {}),
        session_history=state.get("session_history", []),
    ).model_dump()


def trade_advise_node(state: TradeAgentInput) -> dict[str, object]:
    intent = TradeIntent.model_validate(state["trade_intent"])
    data = _purchase_reader().advise(intent, state.get("authorization", ""))
    payload = query_results_payload([GatewayQueryResult.model_validate(fact) for fact in data["facts"].values()])
    return {**payload, "response": purchase_advice_summary(data), "purchase_options": data["options"],
            "rag_mode": "trade_purchase_advice"}

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from app.agents.general_agent import general_agent_node
from app.agents.recommend_agent import recommend_agent_node
from app.agents.support_agent import support_agent_node
from app.agents.trade_agent import trade_agent_node
from app.orchestration.models import AgentName

Worker = Callable[[dict[str, object]], dict[str, object]]


@dataclass(frozen=True)
class CapabilitySpec:
    agent: AgentName
    handler: Worker


CAPABILITY_REGISTRY: dict[str, CapabilitySpec] = {
    "recommend.retrieve": CapabilitySpec("recommend", recommend_agent_node),
    "recommend.rerank": CapabilitySpec("recommend", recommend_agent_node),
    "support.retrieve": CapabilitySpec("support", support_agent_node),
    "support.explain": CapabilitySpec("support", support_agent_node),
    "trade.query": CapabilitySpec("trade", trade_agent_node),
    "general.respond": CapabilitySpec("general", general_agent_node),
}


DEFAULT_CAPABILITY: dict[AgentName, str] = {
    "recommend": "recommend.retrieve",
    "support": "support.retrieve",
    "trade": "trade.query",
    "general": "general.respond",
}


def resolve_capability(capability: str) -> CapabilitySpec:
    try:
        return CAPABILITY_REGISTRY[capability]
    except KeyError as exc:
        raise ValueError(f"unsupported capability: {capability}") from exc

from __future__ import annotations

import json
import logging
from typing import Any

from pydantic import ValidationError

from app.core.config import Settings
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model
from app.tools.models import ToolCall
from app.tools.registry import ToolRegistry

logger = logging.getLogger("food_agent.trade.tool_selector")


def select_trade_tool_calls(
    *,
    settings: Settings,
    registry: ToolRegistry,
    message: str,
    capabilities: set[str],
    required_calls: list[ToolCall],
    chat_model: Any | None = None,
) -> list[ToolCall]:
    """Use one bounded LLM tool-selection round with deterministic guardrails."""
    if not settings.llm_router_enabled or not required_calls:
        return required_calls

    try:
        model = chat_model or build_chat_model(settings)
        if not hasattr(model, "bind_tools"):
            return required_calls
        bound_model = model.bind_tools(registry.llm_schemas())
        response = bound_model.invoke(_selection_messages(message, required_calls))
        selected = _parse_tool_calls(response, registry, capabilities)
    except ChatModelUnavailableError as exc:
        logger.info("trade_tool_selection_fallback reason=%s", exc)
        return required_calls
    except Exception:
        logger.exception("trade_tool_selection_failed")
        return required_calls

    selected_by_name = {call.name: call for call in selected}
    resolved: list[ToolCall] = []
    for fallback in required_calls:
        candidate = selected_by_name.get(fallback.name)
        if candidate is None:
            resolved.append(fallback)
            continue
        merged_arguments = {**candidate.arguments, **fallback.arguments}
        spec = registry.get(candidate.name)
        if spec is None:
            resolved.append(fallback)
            continue
        try:
            spec.args_schema.model_validate(merged_arguments)
        except ValidationError:
            resolved.append(fallback)
            continue
        resolved.append(candidate.model_copy(update={"arguments": merged_arguments}))
    return resolved


def _parse_tool_calls(
    response: object,
    registry: ToolRegistry,
    capabilities: set[str],
) -> list[ToolCall]:
    raw_calls = getattr(response, "tool_calls", None)
    if not isinstance(raw_calls, list):
        return []
    calls: list[ToolCall] = []
    for raw in raw_calls[:8]:
        if not isinstance(raw, dict):
            continue
        name = str(raw.get("name") or "")
        if not registry.supports(name, capabilities):
            continue
        arguments = raw.get("args", raw.get("arguments", {}))
        if isinstance(arguments, str):
            try:
                arguments = json.loads(arguments)
            except json.JSONDecodeError:
                continue
        if not isinstance(arguments, dict):
            continue
        try:
            calls.append(
                ToolCall(
                    id=str(raw.get("id") or ""),
                    name=name,
                    arguments=arguments,
                )
            )
        except ValidationError:
            continue
    return calls


def _selection_messages(
    message: str,
    required_calls: list[ToolCall],
) -> list[tuple[str, str]]:
    allowed_names = list(dict.fromkeys(call.name for call in required_calls))
    return [
        (
            "system",
            (
                "Select the minimum read-only trade tools required for the user request. "
                "Use only the allowed tool names listed by the application. Never request a "
                "write, payment, cancellation, or refund action. Do not invent identifiers."
            ),
        ),
        (
            "human",
            f"User request: {message}\nAllowed required tools: {allowed_names}",
        ),
    ]

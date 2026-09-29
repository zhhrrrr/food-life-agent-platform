from __future__ import annotations

import json

from pydantic import ValidationError

from app.core.metrics import TOOL_EXECUTIONS
from app.tools.models import ToolCall, ToolContext, ToolExecution
from app.tools.registry import ToolRegistry


class ToolExecutor:
    def __init__(self, registry: ToolRegistry, max_calls: int = 8) -> None:
        self.registry = registry
        self.max_calls = max_calls

    def execute(
        self,
        calls: list[ToolCall],
        context: ToolContext,
    ) -> list[ToolExecution]:
        executions: list[ToolExecution] = []
        seen: set[tuple[str, str]] = set()
        for call in calls[: self.max_calls]:
            key = (
                call.name,
                json.dumps(call.arguments, sort_keys=True, default=str),
            )
            if key in seen:
                continue
            seen.add(key)
            execution = self._execute_one(call, context)
            TOOL_EXECUTIONS.labels(tool=call.name, status=execution.status).inc()
            executions.append(execution)
        return executions

    def _execute_one(self, call: ToolCall, context: ToolContext) -> ToolExecution:
        spec = self.registry.get(call.name)
        if spec is None:
            return ToolExecution(call, "unknown_tool", error="tool is not registered")
        if spec.risk != "read":
            return ToolExecution(call, "rejected", error="only read tools are allowed")
        if spec.requires_authorization and not context.authorization.strip():
            return ToolExecution(
                call,
                "missing_authorization",
                error="authorization is required",
            )
        try:
            arguments = spec.args_schema.model_validate(call.arguments)
        except ValidationError as exc:
            return ToolExecution(call, "invalid_arguments", error=str(exc))
        try:
            result = spec.handler(arguments, context)
        except Exception as exc:  # noqa: BLE001 - isolate the registered tool boundary
            return ToolExecution(call, "failed", error=f"{type(exc).__name__}: {exc}")
        return ToolExecution(call, "success", result=result)

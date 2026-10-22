from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field
from app.tools.budget import ToolBudget

ToolRisk = Literal["read", "write"]
ToolExecutionStatus = Literal[
    "success",
    "unknown_tool",
    "invalid_arguments",
    "missing_authorization",
    "rejected",
    "failed",
    "timeout",
    "budget_exceeded",
    "limit_exceeded",
    "busy",
    "uncertain",
]


class ToolContext(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True, extra="forbid")
    authorization: str = ""
    # Fail closed: every caller must explicitly declare its tool scope.
    allowed_tool_names: frozenset[str] = Field(default_factory=frozenset)
    argument_constraints: dict[str, dict[str, Any]] = Field(default_factory=dict)
    # Set only by the confirmed workflow, never from LLM output or MCP input.
    approved_call: ToolCall | None = None
    operation_id: str = ""
    budget: ToolBudget | None = Field(default=None, exclude=True)


class ToolCall(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = ""
    name: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_-]{0,63}$")
    arguments: dict[str, Any] = Field(default_factory=dict)


ToolHandler = Callable[[BaseModel, ToolContext], Any]


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    args_schema: type[BaseModel]
    handler: ToolHandler
    capabilities: frozenset[str]
    requires_authorization: bool = False
    risk: ToolRisk = "read"
    timeout_seconds: float | None = None
    max_attempts: int | None = None
    fallback: ToolHandler | None = None

    def __post_init__(self):
        if self.timeout_seconds is not None and self.timeout_seconds <= 0:
            raise ValueError("tool timeout must be positive")
        if self.max_attempts is not None and not 1 <= self.max_attempts <= 3:
            raise ValueError("tool attempts must be between one and three")

    def llm_schema(self) -> dict[str, object]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.args_schema.model_json_schema(),
            },
        }


@dataclass(frozen=True)
class ToolExecution:
    call: ToolCall
    status: ToolExecutionStatus
    result: Any = None
    error: str = ""
    error_code: str = ""
    attempts: int = 0
    elapsed_ms: float = 0
    retryable: bool = False
    used_fallback: bool = False

    @property
    def succeeded(self) -> bool:
        return self.status == "success"

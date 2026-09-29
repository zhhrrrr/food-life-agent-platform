from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

ToolRisk = Literal["read"]
ToolExecutionStatus = Literal[
    "success",
    "unknown_tool",
    "invalid_arguments",
    "missing_authorization",
    "rejected",
    "failed",
]


class ToolContext(BaseModel):
    authorization: str = ""


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

    @property
    def succeeded(self) -> bool:
        return self.status == "success"

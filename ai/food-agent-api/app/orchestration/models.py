from __future__ import annotations

from typing import Any, Literal

from pydantic import AliasChoices, BaseModel, Field, model_validator

AgentName = Literal["recommend", "support", "trade", "general"]
PlanSource = Literal["semantic", "llm", "rules", "replan"]
TaskStatus = Literal[
    "pending",
    "running",
    "succeeded",
    "failed",
    "skipped",
    "waiting_input",
]


class AgentTask(BaseModel):
    id: str = Field(pattern=r"^[a-z][a-z0-9_]{0,63}$")
    agent: AgentName
    capability: str = Field(min_length=1, max_length=64)
    objective: str = Field(min_length=1, max_length=2000)
    depends_on: list[str] = Field(default_factory=list)
    required: bool = True
    timeout_seconds: float = Field(default=90.0, gt=0, le=300.0)
    max_attempts: int = Field(default=1, ge=1, le=3)

    @model_validator(mode="after")
    def validate_dependencies(self) -> AgentTask:
        if self.id in self.depends_on:
            raise ValueError("task cannot depend on itself")
        if len(self.depends_on) != len(set(self.depends_on)):
            raise ValueError("task dependencies must be unique")
        return self


class ExecutionPlan(BaseModel):
    run_id: str = Field(min_length=8, max_length=64)
    revision: int = Field(default=1, ge=1)
    goal: str = Field(min_length=1, max_length=2000)
    tasks: list[AgentTask] = Field(min_length=1)
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str = Field(min_length=1, max_length=1000)
    source: PlanSource


class PlannedTask(BaseModel):
    id: str
    agent: AgentName
    capability: str
    objective: str = ""
    depends_on: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("depends_on", "dependencies"),
    )
    required: bool | list[str] = True


class PlanDraft(BaseModel):
    tasks: list[PlannedTask] = Field(min_length=1)
    confidence: float = Field(default=0.75, ge=0.0, le=1.0)
    reason: str = Field(default="LLM generated a dynamic execution plan", max_length=1000)


class TaskArtifact(BaseModel):
    """Typed, non-prompt data passed between dependent agent tasks."""

    shop_ids: list[int] = Field(default_factory=list)
    package_ids: list[int] = Field(default_factory=list)
    order_ids: list[int] = Field(default_factory=list)
    facts: list[dict[str, Any]] = Field(default_factory=list)
    references: list[dict[str, Any]] = Field(default_factory=list)

    @classmethod
    def from_payload(cls, payload: dict[str, object]) -> TaskArtifact:
        references = [
            dict(item)
            for item in payload.get("references", [])
            if isinstance(item, dict)
        ]
        facts = [
            dict(item)
            for item in payload.get("facts", [])
            if isinstance(item, dict)
        ]
        shop_ids: list[int] = []
        package_ids: list[int] = []
        order_ids: list[int] = []

        for item in [*references, *facts, *_context_items(payload)]:
            metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
            data = item.get("data") if isinstance(item.get("data"), dict) else {}
            _append_int(shop_ids, metadata.get("shopId") or data.get("shopId"))
            _append_int(package_ids, metadata.get("packageId") or data.get("packageId"))
            _append_int(order_ids, metadata.get("orderId") or data.get("orderId"))
            document_id = str(item.get("id") or "")
            _append_prefixed_id(shop_ids, document_id, "shop-")
            _append_prefixed_id(package_ids, document_id, "package-")
            _append_colon_id(package_ids, document_id, "package:")
            _append_colon_id(order_ids, document_id, "order:")

        return cls(
            shop_ids=list(dict.fromkeys(shop_ids)),
            package_ids=list(dict.fromkeys(package_ids)),
            order_ids=list(dict.fromkeys(order_ids)),
            facts=facts,
            references=references,
        )


class TaskResult(BaseModel):
    task_id: str
    agent: AgentName
    status: TaskStatus
    attempts: int = Field(default=0, ge=0)
    payload: dict[str, object] = Field(default_factory=dict)
    artifact: TaskArtifact = Field(default_factory=TaskArtifact)
    error: str = ""


class DagExecutionResult(BaseModel):
    plan: ExecutionPlan
    task_results: list[TaskResult]
    status: Literal["completed", "partial", "failed", "waiting_input"]
    replan_count: int = Field(default=0, ge=0)


def _context_items(payload: dict[str, object]) -> list[dict[str, Any]]:
    return [
        dict(item)
        for item in payload.get("context", [])
        if isinstance(item, dict)
    ]


def _append_int(target: list[int], value: Any) -> None:
    try:
        if value is not None:
            target.append(int(value))
    except (TypeError, ValueError):
        return


def _append_prefixed_id(target: list[int], value: str, prefix: str) -> None:
    if value.startswith(prefix):
        _append_int(target, value.removeprefix(prefix))


def _append_colon_id(target: list[int], value: str, prefix: str) -> None:
    if not value.startswith(prefix):
        return
    _append_int(target, value.removeprefix(prefix).split(":", 1)[0])

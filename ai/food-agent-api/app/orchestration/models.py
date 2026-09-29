from __future__ import annotations

from typing import Literal

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


class TaskResult(BaseModel):
    task_id: str
    agent: AgentName
    status: TaskStatus
    attempts: int = Field(default=0, ge=0)
    payload: dict[str, object] = Field(default_factory=dict)
    error: str = ""


class DagExecutionResult(BaseModel):
    plan: ExecutionPlan
    task_results: list[TaskResult]
    status: Literal["completed", "partial", "failed", "waiting_input"]
    replan_count: int = Field(default=0, ge=0)

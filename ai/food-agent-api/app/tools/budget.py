"""Request-local tool budgets; credentials and mutable budgets are never persisted."""
from __future__ import annotations

from contextvars import ContextVar
from functools import wraps
from threading import Lock
from time import monotonic


class ToolBudgetExceeded(RuntimeError):
    pass


class ToolBudget:
    def __init__(self, timeout_seconds: float, max_invocations: int):
        self.timeout_seconds = timeout_seconds
        self.max_invocations = max_invocations
        self.invocations = 0
        self._deadline: float | None = None
        self._lock = Lock()

    def remaining(self) -> float:
        with self._lock:
            if self._deadline is None:
                self._deadline = monotonic() + self.timeout_seconds
            return max(0.0, self._deadline - monotonic())

    def claim(self) -> None:
        if self.remaining() <= 0:
            raise ToolBudgetExceeded("tool time budget exhausted")
        with self._lock:
            if self.invocations >= self.max_invocations:
                raise ToolBudgetExceeded("tool attempt budget exhausted")
            self.invocations += 1


CURRENT_TOOL_BUDGET: ContextVar[ToolBudget | None] = ContextVar("tool_budget", default=None)
CURRENT_TOOL_DEADLINE: ContextVar[float | None] = ContextVar("tool_deadline", default=None)


def bounded_http_timeout(configured: float) -> float:
    """Propagate the tool deadline into the actual HTTP transport."""
    deadline = CURRENT_TOOL_DEADLINE.get()
    if deadline is None:
        return configured
    remaining = deadline - monotonic()
    if remaining <= 0:
        raise ToolBudgetExceeded("tool deadline expired before HTTP submission")
    return min(configured, remaining)


def tool_budgeted(function):
    """Share one budget across all tools in a chain/transaction/MCP request."""
    @wraps(function)
    def wrapped(self, *args, **kwargs):
        current = CURRENT_TOOL_BUDGET.get()
        if current is not None:
            return function(self, *args, **kwargs)
        budget = ToolBudget(self.settings.tool_budget_seconds, self.settings.tool_max_invocations)
        token = CURRENT_TOOL_BUDGET.set(budget)
        try:
            return function(self, *args, **kwargs)
        finally:
            CURRENT_TOOL_BUDGET.reset(token)
    return wrapped

from __future__ import annotations

import json
import logging
import random
import time
from concurrent.futures import TimeoutError as FutureTimeoutError
from contextvars import copy_context
from dataclasses import replace

import httpx
from langsmith import traceable
from pydantic import ValidationError

from app.core.config import get_settings
from app.core.langsmith import redact, trace_inputs
from app.core.metrics import TOOL_EXECUTIONS, TOOL_DURATION, TOOL_RETRIES
from app.tools.budget import CURRENT_TOOL_BUDGET, CURRENT_TOOL_DEADLINE, ToolBudget, ToolBudgetExceeded
from app.tools.models import ToolCall, ToolContext, ToolExecution
from app.tools.registry import ToolRegistry
from app.tools.runtime import ToolAlreadyRunning, ToolCapacityExceeded, get_tool_pool

logger = logging.getLogger("food_agent.tools.executor")


class ToolExecutor:
    def __init__(self, registry: ToolRegistry, max_calls: int | None = None, *, settings=None, pool=None) -> None:
        self.registry = registry
        self.settings = settings or get_settings()
        self.max_calls = max_calls if max_calls is not None else self.settings.tool_max_calls
        if self.max_calls <= 0:
            raise ValueError("max_calls must be positive")
        self._pool = pool

    def execute(self, calls: list[ToolCall], context: ToolContext) -> list[ToolExecution]:
        context.budget = context.budget or CURRENT_TOOL_BUDGET.get() or ToolBudget(
            self.settings.tool_budget_seconds, self.settings.tool_max_invocations)
        executions = []
        seen = set()
        for call in calls:
            key = (call.name, json.dumps(call.arguments, sort_keys=True, default=str))
            if key in seen:
                continue
            seen.add(key)
            if len(executions) >= self.max_calls:
                execution = ToolExecution(call, "limit_exceeded", error="tool call limit exceeded",
                                          error_code="TOOL_CALL_LIMIT")
                self._record(execution)
                executions.append(execution)  # One explicit marker for the unscheduled remainder.
                break
            started = time.monotonic()
            execution = self._execute_one(call, context)
            execution = replace(execution, elapsed_ms=(time.monotonic() - started) * 1000)
            self._record(execution)
            executions.append(execution)
        return executions

    def _record(self, execution):
        # Unregistered names never become attacker-controlled metric label values.
        name = execution.call.name if self.registry.get(execution.call.name) else "unknown"
        TOOL_EXECUTIONS.labels(tool=name, status=execution.status).inc()
        TOOL_DURATION.labels(tool=name, status=execution.status).observe(execution.elapsed_ms / 1000)

    @traceable(name="tool.execute", run_type="tool", process_inputs=trace_inputs, process_outputs=redact)
    def _execute_one(self, call: ToolCall, context: ToolContext) -> ToolExecution:
        spec = self.registry.get(call.name)
        if spec is None:
            return ToolExecution(call, "unknown_tool", error="tool is not registered", error_code="TOOL_UNKNOWN")
        if call.name not in context.allowed_tool_names:
            return ToolExecution(call, "rejected", error="tool is outside the current execution scope",
                                 error_code="TOOL_SCOPE_DENIED")
        if spec.risk != "read" and (
            not context.operation_id
            or context.approved_call is None
            or context.approved_call.name != call.name
            or context.approved_call.arguments != call.arguments
        ):
            return ToolExecution(call, "rejected", error="exact write call requires human approval",
                                 error_code="TOOL_APPROVAL_REQUIRED")
        if spec.requires_authorization and not context.authorization.strip():
            return ToolExecution(call, "missing_authorization", error="authorization is required",
                                 error_code="TOOL_AUTH_REQUIRED")
        try:
            arguments = spec.args_schema.model_validate(call.arguments)
        except ValidationError:
            return ToolExecution(call, "invalid_arguments", error="arguments do not match the tool schema",
                                 error_code="TOOL_ARGUMENTS_INVALID")
        values = arguments.model_dump()
        for field, expected in context.argument_constraints.get(call.name, {}).items():
            if field not in values or values[field] != expected:
                return ToolExecution(call, "rejected", error="arguments differ from the trusted execution scope",
                                     error_code="TOOL_ARGUMENT_SCOPE_DENIED")

        # A write is NEVER automatically retried, even if a tool policy requests it.
        attempts = 1 if spec.risk != "read" else min(
            spec.max_attempts or self.settings.tool_read_max_attempts,
            self.settings.tool_read_max_attempts, 3)
        if attempts <= 0:
            raise ValueError("tool attempts must be positive")
        for attempt in range(1, attempts + 1):
            execution = self._invoke(call, context, spec, arguments, spec.handler)
            execution = replace(execution, attempts=attempt if execution.attempts else attempt - 1)
            if not execution.retryable or attempt == attempts:
                break
            delay = self.settings.tool_retry_backoff_seconds * (2 ** (attempt - 1)) * random.uniform(0.8, 1.2)
            if context.budget.remaining() <= delay:
                execution = replace(execution, status="budget_exceeded", error="tool time budget exhausted",
                                    error_code="TOOL_BUDGET_EXCEEDED", retryable=False)
                break
            TOOL_RETRIES.labels(tool=call.name, reason=execution.error_code or "TOOL_TRANSIENT").inc()
            if delay:
                time.sleep(delay)

        # Fallback is application-registered, read-only, and consumes the SAME budget.
        transient_failure = execution.status in {"failed", "timeout", "busy"} or execution.retryable
        if spec.risk == "read" and spec.fallback and transient_failure:
            fallback = self._invoke(call, context, spec, arguments, spec.fallback)
            if fallback.succeeded:
                return replace(fallback, attempts=execution.attempts + fallback.attempts, used_fallback=True)
        return execution

    def _invoke(self, call, context, spec, arguments, handler):
        try:
            context.budget.claim()
        except ToolBudgetExceeded:
            return ToolExecution(call, "budget_exceeded", error="tool time or attempt budget exhausted",
                                 error_code="TOOL_BUDGET_EXCEEDED")
        timeout = min(self.settings.tool_timeout_seconds,
                      spec.timeout_seconds or self.settings.tool_timeout_seconds,
                      context.budget.remaining())
        if timeout <= 0:
            return ToolExecution(call, "budget_exceeded", error="tool time budget exhausted",
                                 error_code="TOOL_BUDGET_EXCEEDED")
        deadline = time.monotonic() + timeout
        inherited = copy_context()

        def run():
            def invoke():
                token = CURRENT_TOOL_DEADLINE.set(deadline)
                budget_token = CURRENT_TOOL_BUDGET.set(context.budget)
                try:
                    if time.monotonic() >= deadline:
                        raise ToolBudgetExceeded("deadline expired before handler start")
                    return handler(arguments, context)
                finally:
                    CURRENT_TOOL_BUDGET.reset(budget_token)
                    CURRENT_TOOL_DEADLINE.reset(token)
            return inherited.run(invoke)

        pool = self._pool or get_tool_pool(self.settings.tool_max_concurrent_calls)
        try:
            future = pool.submit(run, context.operation_id if spec.risk != "read" else "")
        except ToolAlreadyRunning:
            return ToolExecution(call, "uncertain", error="original write is still running; recover the same operation",
                                 error_code="TOOL_WRITE_IN_FLIGHT")
        except ToolCapacityExceeded:
            return ToolExecution(call, "busy", error="tool concurrency capacity exceeded",
                                 error_code="TOOL_CAPACITY_EXCEEDED", attempts=1, retryable=spec.risk == "read")
        except RuntimeError:
            return ToolExecution(call, "busy", error="tool runtime is shutting down",
                                 error_code="TOOL_RUNTIME_CLOSED")
        try:
            result = future.result(timeout=max(0.0, deadline - time.monotonic()))
        except FutureTimeoutError:
            # Future.result timeout cannot stop a running Python thread. Keep its slot
            # and operation guard until completion; never launch a overlapping retry.
            if future.cancelled() or not future.done() or not isinstance(future.exception(), TimeoutError):
                future.cancel()
                return ToolExecution(call, "uncertain" if spec.risk != "read" else "timeout",
                    error="tool timed out; the underlying call may still be running",
                    error_code="TOOL_TIMEOUT", attempts=1)
            return ToolExecution(call, "uncertain" if spec.risk != "read" else "timeout",
                error="tool transport timed out", error_code="TOOL_TIMEOUT", attempts=1,
                retryable=spec.risk == "read")
        except httpx.TimeoutException:
            return ToolExecution(call, "uncertain" if spec.risk != "read" else "timeout",
                error="tool transport timed out", error_code="TOOL_TIMEOUT", attempts=1,
                retryable=spec.risk == "read")
        except httpx.TransportError:
            return ToolExecution(call, "uncertain" if spec.risk != "read" else "failed",
                error="tool network request failed", error_code="TOOL_NETWORK_ERROR", attempts=1,
                retryable=spec.risk == "read")
        except ToolBudgetExceeded:
            return ToolExecution(call, "budget_exceeded", error="tool deadline expired before submission",
                                 error_code="TOOL_BUDGET_EXCEEDED", attempts=1)
        except PermissionError:
            return ToolExecution(call, "missing_authorization", error="tool access denied",
                                 error_code="TOOL_AUTH_DENIED", attempts=1)
        except Exception as exc:
            logger.warning("tool_handler_failed tool=%s error=%s", call.name, type(exc).__name__)
            return ToolExecution(call, "uncertain" if spec.risk != "read" else "failed",
                error="tool handler failed", error_code="TOOL_HANDLER_FAILED", attempts=1)

        retryable = spec.risk == "read" and bool(getattr(result, "retryable", False))
        return ToolExecution(call, "success", result=result, attempts=1, retryable=retryable,
                             error_code=str(getattr(result, "error_code", "")))

from concurrent.futures import Future
from threading import Event
from time import monotonic

import httpx
import pytest
from pydantic import BaseModel, ConfigDict, Field

from app.core.config import Settings
from app.tools.budget import ToolBudget
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext, ToolSpec
from app.tools.registry import ToolRegistry
from app.tools.runtime import ToolPool
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient


class Args(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    value: int = Field(default=1, gt=0)


def settings(**changes):
    return Settings(tool_timeout_seconds=0.15, tool_budget_seconds=1,
                    tool_retry_backoff_seconds=0, **changes)


def scope(*names, **changes):
    return ToolContext(allowed_tool_names=frozenset(names), **changes)


def registry(handler, **policy):
    return ToolRegistry([ToolSpec(name="lookup", description="test lookup", args_schema=Args,
                                 handler=handler, capabilities=frozenset({"lookup"}), **policy)])


@pytest.fixture
def pool():
    pool = ToolPool(2)
    yield pool
    pool.close()


def test_network_retries_are_bounded_and_then_succeed(pool):
    calls = []

    def handler(args, context):
        calls.append(args.value)
        if len(calls) < 3:
            raise httpx.ConnectError("temporarily down")
        return {"value": args.value}

    result = ToolExecutor(registry(handler), settings=settings(), pool=pool).execute(
        [ToolCall(name="lookup", arguments={"value": 7})], scope("lookup"))[0]
    assert result.succeeded and result.result == {"value": 7}
    assert result.attempts == 3 and calls == [7, 7, 7]


def test_cancelled_before_start_releases_capacity_and_operation_guard(monkeypatch):
    pool = ToolPool(1)
    queued = Future()
    original = pool._executor.submit
    monkeypatch.setattr(pool._executor, "submit", lambda handler: queued)
    try:
        future = pool.submit(lambda: pytest.fail("cancelled handler must not execute"), "operation")
        assert future.cancel()
        monkeypatch.setattr(pool._executor, "submit", original)
        assert pool.submit(lambda: "recovered", "operation").result(timeout=1) == "recovered"
    finally:
        pool.close(wait=True)


@pytest.mark.parametrize("http_status,expected_calls", [(503, 3), (429, 3), (401, 1), (403, 1), (404, 1), (400, 1)])
def test_http_retry_classification_and_deadline_propagation(pool, monkeypatch, http_status, expected_calls):
    requests = []

    def get(url, **kwargs):
        requests.append(kwargs)
        return httpx.Response(http_status, json={"code": "500", "data": {}}, request=httpx.Request("GET", url))

    monkeypatch.setattr("app.trade.gateway_client.httpx.get", get)
    config = settings()
    client = TradeGatewayClient(config, timeout_seconds=10)
    result = ToolExecutor(build_trade_tool_registry(client), settings=config, pool=pool).execute(
        [ToolCall(name="trade_get_package_snapshot", arguments={"package_id": 1})],
        scope("trade_get_package_snapshot"))[0]
    assert len(requests) == expected_calls
    assert result.attempts == expected_calls
    assert not result.result.has_data
    # Windows monotonic subtraction can round 0.15 to 0.150000000001.
    assert all(0 < request["timeout"] <= config.tool_timeout_seconds + 1e-9 for request in requests)


@pytest.mark.parametrize("error,expected", [(ValueError("bad handler"), "failed"), (PermissionError("sensitive-upstream-denial"), "missing_authorization")])
def test_non_transient_exceptions_are_not_retried_or_leaked(pool, error, expected):
    calls = []

    def handler(args, context):
        calls.append(1)
        raise error

    result = ToolExecutor(registry(handler), settings=settings(), pool=pool).execute(
        [ToolCall(name="lookup")], scope("lookup"))[0]
    assert result.status == expected and result.attempts == 1
    assert len(calls) == 1
    assert str(error) not in result.error


def test_backend_permission_denial_never_uses_read_fallback(pool):
    fallbacks = []

    def denied(args, context):
        raise PermissionError("not your resource")

    result = ToolExecutor(registry(denied, fallback=lambda args, ctx: fallbacks.append(1)),
                          settings=settings(), pool=pool).execute([ToolCall(name="lookup")], scope("lookup"))[0]
    assert result.status == "missing_authorization" and not fallbacks


def test_tool_overrides_cannot_increase_application_limits(pool):
    observed = []

    def get(url, **kwargs):
        observed.append(kwargs["timeout"])
        raise httpx.ConnectError("down")

    from unittest.mock import patch
    client = TradeGatewayClient(settings(), timeout_seconds=10)
    with patch("app.trade.gateway_client.httpx.get", get):
        result = ToolExecutor(registry(lambda a, c: client.get_package_snapshot(1),
                                       timeout_seconds=30, max_attempts=3),
                              settings=settings(tool_read_max_attempts=1), pool=pool).execute(
            [ToolCall(name="lookup")], scope("lookup"))[0]
    assert result.attempts == 1 and len(observed) == 1 and observed[0] <= 0.15 + 1e-9


def test_business_failure_is_not_a_network_retry(pool):
    calls = []

    def handler(args, context):
        calls.append(1)
        return GatewayQueryResult(capability="lookup", status="not_found", message="not found")

    result = ToolExecutor(registry(handler), settings=settings(), pool=pool).execute(
        [ToolCall(name="lookup")], scope("lookup"))[0]
    assert result.succeeded and not result.result.has_data
    assert result.attempts == 1 and calls == [1]


def test_attempt_budget_includes_retries(pool):
    calls = []

    def handler(args, context):
        calls.append(1)
        raise httpx.ReadTimeout("transient read failure")

    result = ToolExecutor(registry(handler), settings=settings(), pool=pool).execute(
        [ToolCall(name="lookup")], scope("lookup", budget=ToolBudget(1, 2)))[0]
    assert result.status == "budget_exceeded" and result.attempts == 2
    assert len(calls) == 2


def test_same_context_shares_budget_across_execute_calls(pool):
    calls = []
    executor = ToolExecutor(registry(lambda args, context: calls.append(args.value)), settings=settings(), pool=pool)
    context = scope("lookup", budget=ToolBudget(1, 1))
    assert executor.execute([ToolCall(name="lookup")], context)[0].succeeded
    denied = executor.execute([ToolCall(name="lookup")], context)[0]
    assert denied.status == "budget_exceeded" and calls == [1]
    assert "budget" not in context.model_dump()


def test_soft_timeout_returns_promptly_and_keeps_concurrency_slot():
    entered, release, finished = Event(), Event(), Event()
    pool = ToolPool(1)
    calls = []

    def blocked(args, context):
        calls.append(args.value)
        entered.set()
        release.wait(2)
        finished.set()
        return args.value

    try:
        executor = ToolExecutor(registry(blocked), settings=settings(tool_read_max_attempts=1), pool=pool)
        started = monotonic()
        timed_out = executor.execute([ToolCall(name="lookup")], scope("lookup"))[0]
        assert entered.is_set() and timed_out.status == "timeout"
        assert monotonic() - started < 0.8
        # Another executor shares the actual worker limit; the timed-out thread
        # cannot be hidden by creating another ToolExecutor or clearing a slot.
        other = ToolExecutor(registry(blocked), settings=settings(tool_read_max_attempts=1), pool=pool)
        busy = other.execute([ToolCall(name="lookup", arguments={"value": 2})], scope("lookup"))[0]
        assert busy.status == "busy" and calls == [1]
    finally:
        release.set()
        assert finished.wait(2)
        pool.close()


def test_write_timeout_and_inflight_recovery_never_overlap():
    release, finished = Event(), Event()
    pool = ToolPool(2)
    calls = []

    def write(args, context):
        calls.append(context.operation_id)
        release.wait(2)
        finished.set()
        return {"committed": True}

    call = ToolCall(name="lookup")
    context = scope("lookup", operation_id="original-operation", approved_call=call)
    try:
        executor = ToolExecutor(registry(write, risk="write", max_attempts=3), settings=settings(), pool=pool)
        assert executor.execute([call], context)[0].status == "uncertain"
        duplicate = executor.execute([call], context)[0]
        assert duplicate.status == "uncertain" and duplicate.error_code == "TOOL_WRITE_IN_FLIGHT"
        assert calls == ["original-operation"]
    finally:
        release.set()
        assert finished.wait(2)
        pool.close()


def test_write_transport_error_never_retries_or_runs_fallback(pool):
    calls, fallbacks = [], []

    def write(args, context):
        calls.append(context.operation_id)
        raise httpx.ReadTimeout("Java may have committed")

    call = ToolCall(name="lookup")
    executor = ToolExecutor(registry(write, risk="write", max_attempts=3,
        fallback=lambda args, ctx: fallbacks.append(1)), settings=settings(), pool=pool)
    result = executor.execute([call], scope("lookup", operation_id="same-key", approved_call=call))[0]
    assert result.status == "uncertain" and result.attempts == 1
    assert calls == ["same-key"] and not fallbacks


def test_read_fallback_is_bounded_and_explicit(pool):
    calls = []

    def read(args, context):
        calls.append("read")
        raise httpx.ConnectError("temporary failure")

    def fallback(args, context):
        calls.append("fallback")
        return GatewayQueryResult(capability="lookup", status="unavailable", message="live data unavailable")

    result = ToolExecutor(registry(read, max_attempts=2, fallback=fallback), settings=settings(), pool=pool).execute(
        [ToolCall(name="lookup")], scope("lookup"))[0]
    assert result.used_fallback and result.attempts == 3
    assert calls == ["read", "read", "fallback"]
    assert not result.result.has_data


@pytest.mark.parametrize("context,args,expected", [
    (ToolContext(), {"value": 1}, "rejected"),
    (scope("lookup"), {"value": True}, "invalid_arguments"),
    (scope("lookup"), {"value": -1}, "invalid_arguments"),
    (scope("lookup"), {"value": 1, "approved": True}, "invalid_arguments"),
    (scope("lookup", argument_constraints={"lookup": {"value": 1}}), {"value": 2}, "rejected"),
])
def test_invalid_or_out_of_scope_calls_do_not_execute_retry_or_fallback(pool, context, args, expected):
    calls = []
    executor = ToolExecutor(registry(lambda a, c: calls.append("handler"), fallback=lambda a, c: calls.append("fallback")),
                            settings=settings(), pool=pool)
    result = executor.execute([ToolCall(name="lookup", arguments=args)], context)[0]
    assert result.status == expected and result.attempts == 0
    assert not calls


def test_unique_call_limit_returns_explicit_unscheduled_marker(pool):
    calls = []
    executor = ToolExecutor(registry(lambda a, c: calls.append(a.value)), max_calls=1, settings=settings(), pool=pool)
    call = ToolCall(name="lookup")
    results = executor.execute([call, call, ToolCall(name="lookup", arguments={"value": 2})], scope("lookup"))
    assert calls == [1]
    assert [result.status for result in results] == ["success", "limit_exceeded"]


def test_fixed_read_many_degrades_when_call_limit_prevents_some_reads(pool):
    from app.trade.purchase import PurchaseReader

    class Client:
        def get_package_snapshot(self, package_id):
            return GatewayQueryResult(capability="package_snapshot", status="success", data={"packageId": package_id})

    reader = PurchaseReader(Client(), settings())
    reader.executor = ToolExecutor(reader.registry, max_calls=1, settings=settings(), pool=pool)
    results = reader.read_many([
        ("trade_get_package_snapshot", {"package_id": 1}),
        ("trade_list_my_coupons", {}),
        ("trade_list_seckill_activities", {"package_id": 1}),
    ], "auth")
    assert results["trade_get_package_snapshot"].has_data
    assert not results["trade_list_my_coupons"].has_data
    assert results["trade_list_seckill_activities"].error_code == "TOOL_CALL_LIMIT"

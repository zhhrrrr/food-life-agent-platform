from concurrent.futures import Future, ThreadPoolExecutor
from threading import Event, Lock

import pytest
from fastapi import HTTPException
from langgraph.checkpoint.memory import InMemorySaver

from app.core.config import Settings
from app.graph.food_graph import build_food_graph
from app.orchestration.executor import ReadTaskExecutor
from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.registry import CapabilitySpec
from app.orchestration.service import OrchestrationService
from app.orchestration.validator import PlanValidationError
from app.tools.runtime import close_tool_pools, get_tool_pool


@pytest.fixture(autouse=True)
def isolated_worker_pools():
    close_tool_pools()
    yield
    close_tool_pools()


def execute(executor, task):
    return executor.execute_wave([task], {}, authorization="", session_history=[], memory_context_by_agent={})[task.id]


def task(**updates):
    return AgentTask(id="read", agent="recommend", capability="recommend.run", objective="read", **updates)


def test_timeout_keeps_capacity_occupied_across_executor_instances():
    release, entered, finished = Event(), Event(), Event()
    calls = []

    def blocked(_):
        calls.append("started")
        entered.set()
        try:
            assert release.wait(5)
            return {"response": "late"}
        finally:
            finished.set()

    registry = {"recommend.run": CapabilitySpec("recommend", blocked)}
    try:
        first = execute(ReadTaskExecutor(registry=registry, max_parallel_tasks=1), task(timeout_seconds=0.05))
        assert entered.is_set() and first.status == "failed"
        second = execute(ReadTaskExecutor(registry=registry, max_parallel_tasks=1), task(timeout_seconds=0.05))
        assert second.status == "failed" and "capacity" in second.error
        assert second.attempts == 0 and calls == ["started"]
    finally:
        release.set()
        assert finished.wait(5)


def test_timed_out_attempt_cannot_start_another_retry():
    release, finished = Event(), Event()
    calls = []

    def broken(_):
        calls.append("attempt")
        assert release.wait(5)
        raise RuntimeError("late error")

    executor = ReadTaskExecutor(registry={"recommend.run": CapabilitySpec("recommend", broken)}, max_parallel_tasks=1)
    original = executor._execute_task

    def observe(*args):
        try:
            return original(*args)
        finally:
            finished.set()

    executor._execute_task = observe
    try:
        assert execute(executor, task(timeout_seconds=0.05, max_attempts=3)).status == "failed"
    finally:
        release.set()
        assert finished.wait(5)
    assert calls == ["attempt"]


def test_agent_and_nested_tool_have_independent_worker_capacity():
    def nested(_):
        return get_tool_pool(1).submit(lambda: {"response": "nested completed"}).result(timeout=1)

    executor = ReadTaskExecutor(registry={"recommend.run": CapabilitySpec("recommend", nested)}, max_parallel_tasks=1)
    assert execute(executor, task(timeout_seconds=2)).payload["response"] == "nested completed"
    assert executor.execute_wave([], {}, authorization="", session_history=[], memory_context_by_agent={}) == {}


def test_wave_finishes_only_after_worker_capacity_is_released(monkeypatch):
    pool = get_tool_pool(1, namespace="agent")
    releasing, release, returned = Event(), Event(), Event()
    submitted, finish_handler = Event(), Event()
    original = pool._release
    original_submit = pool.submit

    def submit(handler):
        future = original_submit(handler)
        submitted.set()
        return future

    def delayed_release(operation_id):
        releasing.set()
        assert release.wait(5)
        original(operation_id)

    monkeypatch.setattr(pool, "_release", delayed_release)
    monkeypatch.setattr(pool, "submit", submit)

    def handler(_):
        assert finish_handler.wait(5)
        return {"response": "ok"}

    executor = ReadTaskExecutor(registry={"recommend.run": CapabilitySpec("recommend", handler)}, max_parallel_tasks=1)

    def wave():
        result = execute(executor, task(timeout_seconds=2))
        returned.set()
        return result

    with ThreadPoolExecutor(max_workers=1) as caller:
        first = caller.submit(wave)
        try:
            assert submitted.wait(1)
            finish_handler.set()
            assert releasing.wait(1)
            assert not returned.wait(0.05)
        finally:
            finish_handler.set()
            release.set()
        assert first.result(timeout=2).status == "succeeded"
        assert execute(executor, task(timeout_seconds=2)).status == "succeeded"


def make_service(tmp_path):
    return OrchestrationService(Settings(long_term_memory_enabled=False,
        orchestration_checkpoint_path=str(tmp_path / "parent.sqlite")))


def test_async_submissions_are_bounded_and_finished_futures_are_released(tmp_path):
    parent = make_service(tmp_path)
    entered, release = Event(), Event()
    calls, lock = [], Lock()

    def blocked(**kwargs):
        with lock:
            calls.append(kwargs["request_id"])
            if len(calls) == 4:
                entered.set()
        assert release.wait(5)
        return {"response": "done"}

    parent.turn = blocked
    kwargs = dict(user_id="7", session_id="s", authorization="", message="hello")
    try:
        for index in range(4):
            parent.submit(**kwargs, request_id=f"r{index}")
        assert entered.wait(5)
        # Identical in-flight requests still reuse the original run under load.
        assert parent.submit(**kwargs, request_id="r0")["execution_status"] == "running"
        with pytest.raises(HTTPException) as raised:
            parent.submit(**kwargs, request_id="overflow")
        assert raised.value.status_code == 503
        assert len(parent.futures) == 4 and len(parent.future_requests) == 4
    finally:
        release.set()
        parent.close()
    assert not parent.futures and not parent.future_requests


def test_immediately_completed_submission_is_cleaned_up(tmp_path, monkeypatch):
    parent = make_service(tmp_path)

    def complete(handler):
        future = Future()
        future.set_result({"response": "already finished"})
        return future

    monkeypatch.setattr(parent.pool, "submit", complete)
    try:
        parent.submit(user_id="7", session_id="s", authorization="", request_id="r", message="hello")
        assert not parent.futures and not parent.future_requests
    finally:
        parent.close()


@pytest.mark.parametrize("limit", [{"orchestration_max_tasks": 1}, {"orchestration_max_depth": 1}])
def test_replanned_graph_respects_configured_limits(limit):
    first = task()
    second = AgentTask(id="support", agent="support", capability="support.run", objective="rules", depends_on=[first.id])

    class Planner:
        def plan(self, message, history=None):
            return ExecutionPlan(run_id="run-12345678", goal="test", tasks=[first], confidence=1, reason="test", source="rules")

        def replan(self, plan, results):
            return plan.model_copy(update={"revision": 2, "tasks": [first, second], "source": "replan"})

    def broken(_):
        raise RuntimeError("unavailable")

    graph = build_food_graph(Settings(long_term_memory_enabled=False, orchestration_max_replans=1, **limit),
        InMemorySaver(), planner=Planner(), registry={"recommend.run": CapabilitySpec("recommend", broken),
        "support.run": CapabilitySpec("support", lambda _: {"response": "ok"})}, adapter=None)
    with pytest.raises(PlanValidationError, match="more than 1|depth exceeds 1"):
        graph.invoke({"message": "test", "run_id": "run-12345678", "user_id": "1", "session_id": "s"},
            {"configurable": {"thread_id": "limit"}}, context={"authorization": "", "lease_check": lambda: None,
            "paused": lambda: False, "emit": lambda *args: None})

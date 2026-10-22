"""Exercise the real SDK against an in-memory HTTP transport; never send traces out."""
from __future__ import annotations

import importlib
import json
from contextvars import ContextVar
from uuid import uuid4

import pytest
import requests
from langsmith import traceable
from langsmith.run_helpers import get_current_run_tree

from app.core.config import Settings, get_settings
from app.tools.runtime import ToolPool


def integration():
    assert importlib.util.find_spec("app.core.langsmith") is not None, "LangSmith integration is missing"
    return importlib.import_module("app.core.langsmith")


class RecordingAdapter(requests.adapters.BaseAdapter):
    def __init__(self):
        self.requests = []

    def send(self, request, **kwargs):
        self.requests.append(request)
        response = requests.Response()
        response.status_code = 200
        response._content = b'{}'
        response.headers["Content-Type"] = "application/json"
        response.request = request
        return response

    def close(self):
        pass


@pytest.fixture
def captured(monkeypatch):
    module = integration()
    adapter = RecordingAdapter()
    session = requests.Session()
    session.mount("https://langsmith.test", adapter)
    clients = []
    original_client = module.SanitizedClient

    def factory(**kwargs):
        configured_session = kwargs.get("session")
        if configured_session is not None:
            session.trust_env = configured_session.trust_env
            configured_session.close()
        kwargs.update(session=session, auto_batch_tracing=False, info={"version": "test"})
        client = original_client(**kwargs)
        clients.append(client)
        adapter.clients = clients
        return client

    module.get_langsmith_client.cache_clear()
    monkeypatch.setattr(module, "SanitizedClient", factory)
    yield module, adapter, clients
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    module.close_langsmith()
    module.get_langsmith_client.cache_clear()


def enabled_settings(**updates):
    return Settings(langsmith_tracing=True, langsmith_api_key="ls-test-secret",
                    langsmith_endpoint="https://langsmith.test", langsmith_project="food-test", **updates)


def posted(adapter):
    for client in adapter.clients:
        client.flush(timeout=5)
    return [json.loads(request.body) for request in adapter.requests
            if request.method == "POST" and request.url.endswith("/runs")]


def test_settings_reads_dotenv_and_environment_precedence(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    (tmp_path / ".env").write_text("LANGSMITH_TRACING=true\nLANGSMITH_API_KEY=secret\nLANGSMITH_PROJECT=dotenv\n", encoding="utf-8")
    monkeypatch.delenv("LANGSMITH_TRACING", raising=False)
    monkeypatch.setenv("LANGSMITH_PROJECT", "environment")
    get_settings.cache_clear()
    try:
        settings = get_settings()
        assert getattr(settings, "langsmith_tracing", False) is True
        assert settings.langsmith_project == "environment"
        assert settings.langsmith_api_key == "secret"
        assert "langsmith_api_key=" not in repr(settings)
    finally:
        get_settings.cache_clear()


def test_pool_propagates_and_isolates_context_for_each_submission():
    marker = ContextVar("trace-test", default="unset")
    pool = ToolPool(1)
    try:
        token = marker.set("first")
        first = pool.submit(marker.get)
        marker.reset(token)
        token = marker.set("second")
        second = pool.submit(marker.get) if first.result(timeout=2) == "first" else None
        marker.reset(token)
        assert first.result() == "first"
        assert second.result(timeout=2) == "second"
        assert pool.submit(marker.get).result(timeout=2) == "unset"
    finally:
        pool.close(wait=True)


def test_disabled_scope_never_constructs_client(monkeypatch):
    module = integration()
    monkeypatch.setenv("LANGSMITH_TRACING", "true")
    monkeypatch.setattr(module, "get_langsmith_client", lambda *args: pytest.fail("disabled tracing constructed a client"))
    with module.observation_scope(Settings(), "disabled"):
        assert traceable(lambda: 42)() == 42
        assert get_current_run_tree() is None


def test_real_sdk_children_keep_parent_project_and_filter_credentials(captured):
    module, adapter, _ = captured
    from langchain_core.runnables import RunnableLambda

    @traceable(name="worker")
    def worker():
        return {"authorization": "Bearer secret-user-token", "response": "ok", "api_key": "ls-test-secret"}

    pool = ToolPool(1)
    try:
        with module.observation_scope(enabled_settings(), "parent", metadata={"business_run_id": "business-42"}):
            parent = get_current_run_tree()
            result = pool.submit(lambda: RunnableLambda(lambda _: worker()).invoke({"authorization": "Bearer secret-user-token"})).result(timeout=5)
        assert result["response"] == "ok"
    finally:
        pool.close(wait=True)
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    rows = posted(adapter)
    assert {row["name"] for row in rows} >= {"parent", "worker", "RunnableLambda"}
    worker_row = next(row for row in rows if row["name"] == "worker")
    runnable_row = next(row for row in rows if row["name"] == "RunnableLambda")
    assert str(runnable_row["parent_run_id"]) == str(parent.id)
    assert worker_row["parent_run_id"] == runnable_row["id"]
    assert all(row["session_name"] == "food-test" for row in rows)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert "secret-user-token" not in bodies
    assert "ls-test-secret" not in bodies
    assert "business-42" in bodies


def test_redaction_keeps_business_fields_and_does_not_mutate_input():
    module = integration()
    source = {"authorization": "Bearer private", "nested": {"LLM_API_KEY": "private-key"},
              "session_id": "real-session", "package_id": 42, "quantity": 2,
              "message": "库存怎么样", "error": "Authorization: Bearer private"}
    clean = module.redact(source)
    assert clean["authorization"] == "[REDACTED]"
    assert clean["nested"]["LLM_API_KEY"] == "[REDACTED]"
    assert clean["session_id"] != "real-session"
    assert (clean["package_id"], clean["quantity"], clean["message"]) == (42, 2, "库存怎么样")
    assert "private" not in json.dumps(clean)
    assert source["authorization"] == "Bearer private"
    assert module.redact(clean) == clean


@pytest.mark.parametrize('field', ['paymentForm', 'payment_form', 'checkoutForm', 'checkout_form'])
def test_signed_checkout_forms_never_leave_the_sdk(captured, field):
    module, adapter, _ = captured
    payload = {field: '<form>private-signed-checkout</form>', 'payStatus': 'PREPARED'}

    @traceable(name='payment.prepare')
    def prepare():
        return payload

    with module.observation_scope(enabled_settings(), 'checkout-test'):
        assert prepare() is payload
    posted(adapter)
    bodies = '\n'.join(str(request.body) for request in adapter.requests)
    assert 'private-signed-checkout' not in bodies
    assert 'PREPARED' in bodies


def test_setup_failure_is_fail_open_but_preserves_business_exception(monkeypatch):
    module = integration()
    def unavailable(*args):
        raise ConnectionError("trace backend down")
    monkeypatch.setattr(module, "get_langsmith_client", unavailable)
    with module.observation_scope(enabled_settings(), "unavailable"):
        assert get_current_run_tree() is None
    failure = RuntimeError("business failed")
    with pytest.raises(RuntimeError) as caught:
        with module.observation_scope(enabled_settings(), "unavailable"):
            raise failure
    assert caught.value is failure


def test_missing_key_does_not_silently_use_another_client(monkeypatch):
    module = integration()
    monkeypatch.setattr(module, "get_langsmith_client", lambda *args: pytest.fail("missing key"))
    with module.observation_scope(Settings(langsmith_tracing=True), "missing-key"):
        assert get_current_run_tree() is None


def test_full_content_hiding_applies_to_sdk_and_langchain(captured):
    module, adapter, _ = captured
    from langchain_core.runnables import RunnableLambda
    with module.observation_scope(enabled_settings(langsmith_hide_inputs=True, langsmith_hide_outputs=True), "hidden", inputs={"message": "private-question"}):
        assert RunnableLambda(lambda _: {"response": "private-answer"}).invoke({"message": "private-question"}) == {"response": "private-answer"}
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    posted(adapter)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert "private-question" not in bodies
    assert "private-answer" not in bodies


def test_real_persistent_parent_uses_configured_tracing(captured, tmp_path):
    module, adapter, _ = captured
    from app.orchestration.models import AgentTask, ExecutionPlan
    from app.orchestration.registry import CapabilitySpec
    from app.orchestration.service import OrchestrationService
    from app.trade.service import TradeWorkflowService

    class Planner:
        def plan(self, message, history=None):
            return ExecutionPlan(run_id=uuid4().hex, goal=message, source="rules", confidence=1, reason="test",
                                 tasks=[AgentTask(id="hello", agent="general", capability="general.respond", objective=message)])

    settings = enabled_settings(orchestration_checkpoint_path=str(tmp_path / "parent.sqlite"),
                                trade_checkpoint_path=str(tmp_path / "child.sqlite"))
    child = TradeWorkflowService(settings)
    service = OrchestrationService(settings, planner=Planner(), trade_service=child,
        registry={"general.respond": CapabilitySpec("general", lambda state: {"response": "hello"})})
    try:
        result = service.turn(user_id="7", authorization="Bearer private-token", session_id="private-session", message="你好", request_id="request-1")
        assert result["execution_status"] == "completed"
    finally:
        service.close()
        child.close()
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    rows = posted(adapter)
    assert any(row["name"] == "food_agent.turn" for row in rows)
    assert any(row["name"] == "agent.task" for row in rows)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert result["run_id"] in bodies
    assert "request-1" in bodies
    assert "private-token" not in bodies
    assert "private-session" not in bodies


def test_trade_resume_after_restart_is_linked_by_business_run(captured, tmp_path):
    from test_persistent_orchestration import services, workflow_task, start, confirm
    _, adapter, _ = captured
    parent, child, _, _ = services(tmp_path, [workflow_task()])
    config = enabled_settings()
    def configure(parent, child):
        fields = {key: getattr(config, key) for key in type(config).model_fields if key.startswith("langsmith_")}
        parent.settings = parent.settings.model_copy(update=fields)
        child.settings = child.settings.model_copy(update=fields)
    configure(parent, child)
    try:
        pending = start(parent)
        assert pending["execution_status"] == "waiting_input"
    finally:
        parent.close()
        child.close()
    parent, child, _, actions = services(tmp_path, [workflow_task()])
    configure(parent, child)
    try:
        finished = confirm(parent, pending)
        assert finished["execution_status"] == "completed"
        assert len(actions.calls) == 1
    finally:
        parent.close()
        child.close()
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    rows = posted(adapter)
    roots = [row for row in rows if row["name"] == "food_agent.turn"]
    assert len(roots) == 2
    assert roots[0]["id"] != roots[1]["id"]
    assert {row["extra"]["metadata"]["business_run_id"] for row in roots} == {pending["run_id"]}
    assert {row["extra"]["metadata"]["request_id"] for row in roots} == {"start", "confirm"}
    assert len([row for row in rows if row["name"] == "trade.turn"]) == 2
    assert any(row["name"] == "tool.execute" for row in rows)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert pending["trade"]["interrupt"]["confirmation_id"] not in bodies


def test_llm_trace_keeps_model_token_usage_and_removes_credentials(captured):
    import httpx
    from langchain_openai import ChatOpenAI
    module, adapter, _ = captured
    transport = httpx.MockTransport(lambda request: httpx.Response(200, json={
        "id": "chatcmpl-test", "object": "chat.completion", "created": 0, "model": "gpt-4o-mini",
        "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}],
        "usage": {"prompt_tokens": 10, "completion_tokens": 2, "total_tokens": 12}}))
    with httpx.Client(transport=transport) as http_client:
        model = ChatOpenAI(model="gpt-4o-mini", api_key="llm-private-key", http_client=http_client)
        with module.observation_scope(enabled_settings(), "llm-parent"):
            assert model.invoke("hello").content == "ok"
    from langchain_core.tracers.langchain import wait_for_all_tracers
    wait_for_all_tracers()
    rows = posted(adapter)
    assert any(row["run_type"] == "llm" for row in rows)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert "llm-private-key" not in bodies
    assert "total_tokens" in bodies and "gpt-4o-mini" in bodies


def test_exception_traces_filter_bearer_tokens_and_preserve_exception(captured):
    module, adapter, _ = captured
    failure = RuntimeError("upstream rejected Bearer private-exception-token")
    with pytest.raises(RuntimeError) as caught:
        with module.observation_scope(enabled_settings(), "failed-call"):
            raise failure
    assert caught.value is failure
    posted(adapter)
    bodies = "\n".join(str(request.body) for request in adapter.requests)
    assert "private-exception-token" not in bodies
    assert "RuntimeError" in bodies


def test_langsmith_can_bypass_system_proxy_without_changing_process_proxy(captured, monkeypatch):
    module, _, _ = captured
    monkeypatch.setenv("HTTPS_PROXY", "http://system-proxy.invalid:8080")
    client = module.client_for(enabled_settings(langsmith_trust_env=False))
    assert client.session.trust_env is False
    import os
    assert os.environ["HTTPS_PROXY"] == "http://system-proxy.invalid:8080"

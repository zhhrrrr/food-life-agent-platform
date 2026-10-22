"""Controlled suite contract and real production graph integration.

No existing product fixtures are reused: frozen expectations are declared before
the suite runs. Regressions in the production graph must remain visible.
"""
import importlib.util
import json

import pytest

from app.core.config import Settings

CASE_IDS = [
    "controlled_preview", "controlled_approval", "controlled_rejection",
    "controlled_wrong_confirmation", "controlled_ownership", "controlled_session",
    "controlled_replay", "controlled_payload_conflict", "controlled_restart",
    "controlled_timeout_recovery", "controlled_read_failure",
    "controlled_action_failure", "controlled_history_correction",
    "controlled_missing_parameters", "controlled_stale_preview",
    "controlled_response_loss", "controlled_auth_required",
    "controlled_upstream_failure",
]


def suite():
    assert importlib.util.find_spec("evaluation.controlled_evaluation") is not None, (
        "controlled runtime evaluator has not been implemented"
    )
    from evaluation import controlled_evaluation
    return controlled_evaluation


def test_frozen_cases_are_independent_and_have_explicit_safety_subsets():
    cases = suite().load_cases()
    assert [case["id"] for case in cases] == CASE_IDS
    for case in cases:
        assert case["inputs"]["case_id"] == case["id"]
        checks = case["outputs"]["required_checks"]
        assert checks and len(checks) == len(set(checks))
        assert set(case["outputs"].get("safety_checks", [])) <= set(checks)
    correction = next(c for c in cases if c["id"] == "controlled_history_correction")
    assert "safety_checks" not in correction["outputs"]


@pytest.mark.parametrize("case_id", CASE_IDS)
def test_real_controlled_runtime_satisfies_frozen_expectations(case_id):
    module = suite()
    case = next(case for case in module.load_cases() if case["id"] == case_id)
    result = module.run_case(case["inputs"], Settings())
    assert result["execution_tier"] == "controlled_runtime"
    assert result["duration_ms"] >= 0
    assert set(result["checks"]) == set(case["outputs"]["required_checks"])
    assert all(type(value) is bool for value in result["checks"].values())
    assert all(result["checks"].values()), result
    assert "error" not in result
    assert result["evidence"]["external_dependencies"] == "controlled_sqlite_gateway"
    assert result["evidence"]["real_payment"] is False
    assert result["evidence"]["resources_closed"] is True
    encoded = json.dumps(result)
    for sensitive in ("Bearer", "confirmation_id", "authorization", "paymentForm"):
        assert sensitive not in encoded


def test_evaluator_does_not_credit_success_without_matching_business_state(monkeypatch):
    module = suite()

    def false_success(self, args, context):
        from app.trade.actions import ActionResult
        return ActionResult(status="success", data={"orderId": 9001, "orderStatus": "WAIT_PAY"})

    monkeypatch.setattr(module._ControlledGateway, "create", false_success)
    result = module.run_case({"case_id": "controlled_approval"}, Settings())
    assert result["checks"]["child_completed"] is True
    assert result["checks"]["matching_order_persisted"] is False
    assert result["checks"]["one_business_write"] is False


def test_unexpected_exception_fails_every_required_check_without_leaking_message(monkeypatch):
    module = suite()

    def crash(self, **kwargs):
        raise RuntimeError("Bearer secret confirmation_id=private paymentForm=<form>")

    monkeypatch.setattr(module._ControlledRuntime, "start", crash)
    result = module.run_case({"case_id": "controlled_preview"}, Settings())
    assert result["error"] == "RuntimeError"
    assert result["checks"] and not any(result["checks"].values())
    assert result["evidence"]["resources_closed"] is True
    assert "secret" not in json.dumps(result)


def test_unknown_case_is_not_silently_accepted():
    with pytest.raises(ValueError):
        suite().run_case({"case_id": "made-up-case"}, Settings())


def test_case_closes_sqlite_handles_and_removes_its_temporary_directory(monkeypatch):
    from pathlib import Path
    from tempfile import TemporaryDirectory
    module = suite()
    paths = []

    def record_directory(**kwargs):
        temporary = TemporaryDirectory(**kwargs)
        paths.append(Path(temporary.name))
        return temporary

    monkeypatch.setattr(module, "TemporaryDirectory", record_directory)
    result = module.run_case({"case_id": "controlled_preview"}, Settings())
    assert result["evidence"]["resources_closed"] is True
    assert paths and all(not path.exists() for path in paths)


@pytest.mark.parametrize("failure_boundary", ["parent_construction", "evidence_read"])
def test_failure_during_setup_or_evidence_read_still_closes_databases(monkeypatch, failure_boundary):
    from pathlib import Path
    from tempfile import TemporaryDirectory
    module = suite()
    paths = []

    def record_directory(**kwargs):
        temporary = TemporaryDirectory(**kwargs)
        paths.append(Path(temporary.name))
        return temporary

    def crash(*args, **kwargs):
        raise RuntimeError("controlled setup or evidence failure")

    monkeypatch.setattr(module, "TemporaryDirectory", record_directory)
    if failure_boundary == "parent_construction":
        monkeypatch.setattr(module, "OrchestrationService", crash)
    else:
        monkeypatch.setattr(module._ControlledGateway, "stats", crash)
    result = module.run_case({"case_id": "controlled_preview"}, Settings())
    assert result["error"] == "RuntimeError"
    # stats() is also used inside the scenario before observations are returned.
    assert not any(result["checks"].values())
    assert set(result["unverified_checks"]) == set(result["checks"])
    assert result["evidence"]["resources_closed"] is True
    assert paths and all(not path.exists() for path in paths)


def test_controlled_runtime_overrides_real_database_model_and_memory_configuration():
    result = suite().run_case({"case_id": "controlled_preview"}, Settings(
        runtime_database_url="postgresql://unreachable.invalid/evaluation_must_not_connect",
        llm_router_enabled=True, semantic_router_enabled=True,
        llm_api_key="placeholder-must-not-be-used", long_term_memory_enabled=True,
        session_memory_enabled=True, es_enabled=True, milvus_enabled=True,
    ))
    assert result["checks"] == {"confirmation_required": True, "exact_preview": True, "no_business_write": True}
    assert "error" not in result
    assert "placeholder-must-not-be-used" not in json.dumps(result)


def test_history_correction_does_not_pass_when_its_read_prerequisite_fails(monkeypatch):
    module = suite()

    def unavailable(self, authorization, page_size=10):
        from app.trade.gateway_client import GatewayQueryResult
        return GatewayQueryResult(capability="order_query", status="unavailable")

    monkeypatch.setattr(module._ControlledGateway, "list_orders", unavailable, raising=False)
    result = module.run_case({"case_id": "controlled_history_correction"}, Settings())
    assert result["checks"]["read_dependencies_available"] is False
    assert not all(result["checks"].values())


def test_response_loss_recovery_finishes_parent_after_durable_child_completion():
    result = suite().run_case({"case_id": "controlled_response_loss"}, Settings())
    assert result["checks"]["parent_completed_after_explicit_recovery"] is True
    assert result["evidence"]["latest"]["parent_status"] == "completed"


def test_cleanup_error_cannot_erase_observed_safety_failure(monkeypatch):
    module = suite()
    real_scenario, real_close = module._scenario, module._ControlledRuntime.close
    def unsafe_observation(runtime, inputs):
        observed = real_scenario(runtime, inputs)
        observed["no_business_write"] = False
        return observed
    def close_then_fail(runtime):
        real_close(runtime)
        raise RuntimeError("cleanup failed after observation")
    monkeypatch.setattr(module, "_scenario", unsafe_observation)
    monkeypatch.setattr(module._ControlledRuntime, "close", close_then_fail)
    result = module.run_case({"case_id": "controlled_preview"}, Settings())
    assert result["error"] == "RuntimeError"
    assert result["checks"]["confirmation_required"] is True
    assert result["checks"]["no_business_write"] is False
    assert "no_business_write" not in result.get("unverified_checks", [])

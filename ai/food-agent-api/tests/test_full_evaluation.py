from __future__ import annotations

import importlib
from types import SimpleNamespace

import pytest


def module():
    assert importlib.util.find_spec("evaluation.full_evaluation") is not None, "Full-suite evaluator is missing"
    return importlib.import_module("evaluation.full_evaluation")


def reference(safety=False):
    result = {"required_checks": ["state_correct", "answer_truthful"]}
    if safety:
        result["safety_checks"] = ["state_correct"]
    return result


@pytest.mark.parametrize("checks", [
    {}, {"state_correct": True}, {"state_correct": 1, "answer_truthful": True},
    {"state_correct": "true", "answer_truthful": True},
    {"state_correct": True, "answer_truthful": True, "undeclared": True},
    {"state_correct": False, "answer_truthful": True},
])
def test_missing_invalid_or_false_assertions_cannot_pass(checks):
    assert module().suite_scores({"checks": checks}, reference())["task_success"] == 0


def test_valid_case_and_conditional_safety_scores():
    actual = {"checks": {"state_correct": True, "answer_truthful": True}}
    scores = module().suite_scores(actual, reference())
    assert scores["task_success"] == 1
    assert "safety_success" not in scores
    assert module().suite_scores(actual, reference(True))["safety_success"] == 1
    assert module().suite_scores({**actual, "error": "BackendError"}, reference())["task_success"] == 0


@pytest.mark.parametrize("ref", [
    {"required_checks": []}, {"required_checks": ["same", "same"]},
    {"required_checks": ["a"], "safety_checks": ["undeclared"]},
])
def test_invalid_reference_contract_raises(ref):
    with pytest.raises(ValueError):
        module().suite_scores({"checks": {}}, ref)


def row(identifier, trial, good=True, safety=False):
    return {"id": identifier, "trial": trial, "category": "test",
            "reference": reference(safety),
            "outputs": {"checks": {"state_correct": good, "answer_truthful": True},
                        "duration_ms": 10, "execution_tier": "test"}}


def test_summary_denominators_and_repeated_reliability_are_explicit():
    rows = [row("a", 1, safety=True), row("a", 2, safety=True), row("b", 1), row("b", 2, False)]
    report = module().summarize_suite(rows, expected_case_ids=["a", "b"], repetitions=2)
    assert report["task_success"] == {"passed": 3, "total": 4, "rate": 0.75}
    assert report["metrics"]["safety_success"]["total"] == 2
    assert report["reliability"] == {"all_trials_passed": 1, "cases": 2, "rate": 0.5,
                                     "incomplete_cases": [], "changed_outcome_cases": ["b"]}


def test_missing_repetition_is_not_counted_as_reliable():
    report = module().summarize_suite([row("a", 1)], expected_case_ids=["a"], repetitions=2)
    assert report["reliability"]["all_trials_passed"] == 0
    assert report["reliability"]["incomplete_cases"] == ["a"]


def test_duplicate_trial_is_rejected():
    with pytest.raises(ValueError):
        module().summarize_suite([row("a", 1), row("a", 1)], expected_case_ids=["a"], repetitions=1)


def test_safety_failures_and_unverified_checks_are_reported_separately():
    a, b = row("a", 1, False, True), row("b", 1, safety=True)
    b["outputs"]["checks"].pop("state_correct")
    report = module().summarize_suite([a, b], expected_case_ids=["a", "b"], repetitions=1)
    assert report["safety_failed_assertions"] == 1
    assert report["safety_unverified_assertions"] == 1
    assert not module().passes_gate(report, min_score=0)


def test_backend_failure_is_unverified_safety_not_observed_unsafe_behavior():
    item = row("a", 1, False, True)
    item["outputs"]["error"] = "ConnectError"
    item["outputs"]["unverified_checks"] = ["state_correct", "answer_truthful"]
    report = module().summarize_suite([item], expected_case_ids=["a"], repetitions=1)
    assert report["safety_failed_assertions"] == 0
    assert report["safety_unverified_assertions"] == 1


def test_observed_unsafe_action_survives_later_cleanup_error():
    item = row("a", 1, False, True)
    item["outputs"]["error"] = "CleanupError"
    report = module().summarize_suite([item], expected_case_ids=["a"], repetitions=1)
    assert report["safety_failed_assertions"] == 1
    assert report["safety_unverified_assertions"] == 0


@pytest.mark.parametrize("unverified", [["unknown"], ["state_correct", "state_correct"], "state_correct"])
def test_invalid_unverified_check_names_cannot_pass(unverified):
    actual = row("a", 1)["outputs"]
    actual["unverified_checks"] = unverified
    assert module().suite_scores(actual, reference())["task_success"] == 0


def test_explicit_unverified_check_cannot_pass_even_if_placeholder_is_true():
    actual = row("a", 1, safety=True)["outputs"]
    actual["unverified_checks"] = ["state_correct"]
    scores = module().suite_scores(actual, reference(True))
    assert scores["task_success"] == scores["safety_success"] == scores["check.state_correct"] == 0


def test_summary_handles_malformed_unverified_field_without_crashing():
    item = row("a", 1, safety=True)
    item["outputs"]["unverified_checks"] = "malformed"
    report = module().summarize_suite([item], expected_case_ids=["a"], repetitions=1)
    assert report["safety_unverified_assertions"] == 1
    assert report["metrics"]["assertion_schema_valid"]["rate"] == 0


@pytest.mark.parametrize("checks", [[True], "invalid", True])
def test_summary_retains_malformed_observation_as_failure(checks):
    item = row("a", 1, safety=True)
    item["outputs"]["checks"] = checks
    report = module().summarize_suite([item], expected_case_ids=["a"], repetitions=1)
    assert report["task_success"]["passed"] == 0
    assert report["safety_unverified_assertions"] == 1


def test_verified_safe_side_effects_survive_unrelated_parent_completion_error():
    actual = row("a", 1, safety=True)["outputs"]
    actual["error"] = "InvalidUpdateError"
    scores = module().suite_scores(actual, reference(True))
    assert scores["task_success"] == 0
    assert scores["safety_success"] == 1


def test_malformed_assertion_schema_blocks_even_zero_score_gate():
    item = row("a", 1)
    item["outputs"]["checks"].pop("answer_truthful")
    report = module().summarize_suite([item], expected_case_ids=["a"], repetitions=1)
    assert not module().passes_gate(report, min_score=0)


def test_source_manifest_includes_runner_changes(tmp_path):
    scripts = tmp_path / "scripts"
    scripts.mkdir()
    runner = scripts / "evaluate_full_agent.py"
    runner.write_text("version = 1", encoding="utf-8")
    before = module().source_manifest(tmp_path)
    runner.write_text("version = 2", encoding="utf-8")
    assert module().source_manifest(tmp_path)["sha256"] != before["sha256"]


def test_cli_returns_nonzero_for_failed_evaluation(tmp_path, monkeypatch):
    from scripts import evaluate_full_agent
    fake = SimpleNamespace(load_cases=lambda: [{"id": "a", "inputs": {"case_id": "a"}, "outputs": reference()}],
                           run_case=lambda inputs, settings: row("a", 1, False)["outputs"])
    monkeypatch.setattr(evaluate_full_agent, "get_suite", lambda name: fake)
    output = tmp_path / "report.json"
    assert evaluate_full_agent.main(["--suite", "controlled", "--output", str(output), "--min-score", "1"]) == 1
    assert output.is_file()


def test_runner_keeps_trial_bookkeeping_out_of_frozen_suite_inputs(tmp_path, monkeypatch):
    from scripts import evaluate_full_agent as cli
    inputs = {"case_id": "a", "message": "frozen"}
    def run_case(actual, settings):
        assert actual == inputs, "Runner changed frozen suite inputs"
        return row("a", 1)["outputs"]
    fake = SimpleNamespace(load_cases=lambda: [{"id": "a", "inputs": inputs, "outputs": reference()}], run_case=run_case)
    monkeypatch.setattr(cli, "get_suite", lambda name: fake)
    assert cli.main(["--suite", "answer", "--repetitions", "2", "--output", str(tmp_path / "r.json")]) == 0


def test_publish_uses_supported_project_url_and_finalizes_report(tmp_path, monkeypatch):
    from contextlib import nullcontext

    from app.core.config import Settings
    from scripts import evaluate_full_agent as cli

    case = {"id": "a", "inputs": {"case_id": "a"}, "outputs": reference()}
    fake_suite = SimpleNamespace(load_cases=lambda: [case], run_case=lambda inputs, settings: row("a", 1)["outputs"])
    class Experiment(list):
        experiment_name = "sdk-compatible-test"
    class Client:
        # Deliberately no get_project_url: the installed SDK does not expose it.
        def evaluate(self, target, **kwargs):
            target({"case_id": "a", "trial": 1})
            return Experiment([{}])
        def read_project(self, **kwargs):
            return SimpleNamespace(id="experiment-1", url="https://example.test/experiment-1")
    monkeypatch.setattr(cli, "get_suite", lambda name: fake_suite)
    monkeypatch.setattr(cli, "get_settings", lambda: Settings(_env_file=None, langsmith_api_key="fake-test-key"))
    monkeypatch.setattr(cli, "client_for", lambda settings: Client())
    monkeypatch.setattr(cli, "sync_dataset", lambda *args: "frozen-dataset")
    monkeypatch.setattr(cli, "observation_scope", lambda *args, **kwargs: nullcontext(None))
    output = tmp_path / "published.json"
    assert cli.main(["--suite", "controlled", "--publish", "--output", str(output)]) == 0
    import json
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["final"] is True
    assert report["experiment_url"] == "https://example.test/experiment-1"

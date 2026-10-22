from __future__ import annotations

import importlib
import json
from types import SimpleNamespace
from uuid import uuid4

import pytest


def evaluation():
    assert importlib.util.find_spec("evaluation.agent_evaluation") is not None, "Agent evaluators are missing"
    return importlib.import_module("evaluation.agent_evaluation")


def reference():
    return {"allowed_sources": ["llm"], "tasks": [
        {"id": "facts", "capability": "trade.query", "depends_on": [], "trade_intent": {"package_id": 42}},
        {"id": "policy", "capability": "support.run", "depends_on": ["facts"]},
    ]}


def output():
    return {"source": "llm", "tasks": [
        {"id": "answer_7", "capability": "support.run", "depends_on": ["lookup_9"], "trade_intent": None},
        {"id": "lookup_9", "capability": "trade.query", "depends_on": [], "trade_intent": {"package_id": 42, "action": "query"}},
    ], "duration_ms": 10}


def test_plan_scoring_ignores_generated_ids_and_task_list_order():
    scores = evaluation().planner_scores(output(), reference())
    assert scores == {"capabilities_correct": 1.0, "dependencies_correct": 1.0,
                      "parameters_correct": 1.0, "source_correct": 1.0, "strict_success": 1.0}


def read_query_reference():
    return {"tasks": [
        {"id": "facts", "capability": "trade.query", "depends_on": [],
         "trade_intent": None, "objective_ids": {"package": "42"}},
        {"id": "policy", "capability": "support.run", "depends_on": ["facts"]},
    ]}


def read_query_output(objective="查询套餐42库存", intent=None):
    return {"source": "llm", "tasks": [
        {"id": "answer_7", "capability": "support.run", "depends_on": ["lookup_9"]},
        {"id": "lookup_9", "capability": "trade.query", "depends_on": [],
         "objective": objective, "trade_intent": intent},
    ]}


def test_read_query_null_intent_with_objective_id_ignores_task_ids_and_order():
    scores = evaluation().planner_scores(read_query_output(), read_query_reference())
    assert scores["parameters_correct"] == 1
    assert scores["strict_success"] == 1


@pytest.mark.parametrize("objective", ["查询套餐999库存", "查询套餐库存", "查询订单42库存", None])
def test_read_query_missing_or_wrong_objective_id_fails_parameters(objective):
    scores = evaluation().planner_scores(read_query_output(objective=objective), read_query_reference())
    assert scores["capabilities_correct"] == scores["dependencies_correct"] == 1
    assert scores["parameters_correct"] == scores["strict_success"] == 0


@pytest.mark.parametrize("intent", [{}, {"action": "query", "package_id": 42}, {"action": "create", "package_id": 42}])
def test_explicit_null_reference_rejects_nonnull_intents(intent):
    scores = evaluation().planner_scores(read_query_output(intent=intent), read_query_reference())
    assert scores["parameters_correct"] == scores["strict_success"] == 0


def test_absent_reference_intent_does_not_constrain_actual_intent():
    expected = read_query_reference()
    del expected["tasks"][0]["trade_intent"]
    actual = read_query_output(intent={"action": "query", "package_id": 42})
    assert evaluation().planner_scores(actual, expected)["strict_success"] == 1


@pytest.mark.parametrize("mutation,failed_score", [
    ("dependency", "dependencies_correct"), ("parameter", "parameters_correct"),
    ("extra_task", "capabilities_correct"), ("fallback", "source_correct"),
    ("cycle", "dependencies_correct"),
])
def test_wrong_plans_and_fallback_never_count_as_strict_success(mutation, failed_score):
    actual = output()
    if mutation == "dependency":
        actual["tasks"][0]["depends_on"] = []
    elif mutation == "parameter":
        actual["tasks"][1]["trade_intent"]["package_id"] = 999
    elif mutation == "extra_task":
        actual["tasks"].append({"id": "extra", "capability": "trade.query", "depends_on": []})
    elif mutation == "fallback":
        actual["source"] = "rules"
    else:
        actual["tasks"][1]["depends_on"] = ["answer_7"]
    scores = evaluation().planner_scores(actual, reference())
    assert scores[failed_score] == 0
    assert scores["strict_success"] == 0


def test_parameter_checks_bind_to_the_same_task_as_dependencies():
    expected = {"allowed_sources": ["llm"], "tasks": [
        {"id": "a", "capability": "trade.query", "depends_on": [], "trade_intent": {"package_id": 1}},
        {"id": "b", "capability": "trade.query", "depends_on": ["a"], "trade_intent": {"package_id": 2}},
    ]}
    actual = {"source": "llm", "tasks": [
        {"id": "x", "capability": "trade.query", "depends_on": [], "trade_intent": {"package_id": 2}},
        {"id": "y", "capability": "trade.query", "depends_on": ["x"], "trade_intent": {"package_id": 1}},
    ]}
    assert evaluation().planner_scores(actual, expected)["strict_success"] == 0


def test_greeting_can_allow_rules_without_counting_as_model_output():
    expected = {"allowed_sources": ["rules"], "tasks": [{"id": "hello", "capability": "general.respond", "depends_on": []}]}
    actual = {"source": "rules", "tasks": [{"id": "a", "capability": "general.respond", "depends_on": []}]}
    assert evaluation().planner_scores(actual, expected)["strict_success"] == 1


def test_rag_scores_keep_negative_cases_out_of_positive_recall():
    module = evaluation()
    scores = module.rag_scores({"actual_ids": ["noise", "right"], "top_k": 5}, {"expected_ids": ["right"]})
    assert scores["hit_rate_at_k"] == 1
    assert scores["mrr_at_k"] == 0.5
    negative = module.rag_scores({"actual_ids": [], "top_k": 5}, {"expected_ids": [], "should_reject": True})
    assert negative == {"rejection_success": 1.0}


def test_dataset_sync_is_versioned_and_recovers_partial_upload():
    module = evaluation()
    class DatasetClient:
        def __init__(self):
            self.dataset = None
            self.examples = []
        def has_dataset(self, **kwargs):
            return self.dataset is not None
        def create_dataset(self, dataset_name, **kwargs):
            self.dataset = SimpleNamespace(id=uuid4(), name=dataset_name)
            return self.dataset
        def read_dataset(self, **kwargs):
            return self.dataset
        def list_examples(self, **kwargs):
            return iter(self.examples)
        def create_examples(self, *, examples, **kwargs):
            self.examples.extend(SimpleNamespace(**example) for example in examples)
    client = DatasetClient()
    cases = [{"id": "first", "inputs": {"message": "查询套餐42库存"}, "outputs": reference()},
             {"id": "second", "inputs": {"message": "你好"}, "outputs": {"tasks": []}}]
    name = module.sync_dataset(client, "food-test", "planner", cases)
    assert len(client.examples) == 2
    client.examples.pop()
    assert module.sync_dataset(client, "food-test", "planner", cases) == name
    assert len(client.examples) == 2
    assert module.sync_dataset(client, "food-test", "planner", cases) == name
    assert len(client.examples) == 2
    assert "planner" in name


def test_local_target_evaluates_planner_without_workers_or_tracing():
    from app.core.config import Settings
    module = evaluation()
    target = module.make_target("planner", Settings(llm_router_enabled=False), publish=False)
    actual = target({"message": "查询套餐42库存"})
    assert actual["source"] == "rules"
    assert [task["capability"] for task in actual["tasks"]] == ["trade.query"]
    assert actual["duration_ms"] >= 0


def test_shipped_datasets_have_valid_unique_cases_and_domains():
    module = evaluation()
    planner = module.load_examples("planner", module.DEFAULT_CASES["planner"])
    rag = module.load_examples("rag", module.DEFAULT_CASES["rag"])
    assert len(planner) >= 22
    assert len({case["id"] for case in planner}) == len(planner)
    assert len(rag) == 62
    assert any(case["outputs"].get("should_reject") for case in rag)
    assert any("不要" in case["inputs"]["message"] for case in planner)
    assert any(case["inputs"].get("history") for case in planner)


def test_invalid_dataset_is_rejected_before_any_model_call(tmp_path):
    module = evaluation()
    path = tmp_path / "cases.jsonl"
    case = {"id": "bad", "inputs": {"message": "库存"}, "outputs": {"allowed_sources": ["llm"], "tasks": [
        {"id": "task", "capability": "trade.query", "depends_on": ["missing"]}]}}
    path.write_text(json.dumps(case), encoding="utf-8")
    with pytest.raises(ValueError, match="dependency|DAG"):
        module.load_examples("planner", path)


def test_evaluators_run_with_the_real_langsmith_experiment_runner():
    from datetime import datetime, timezone
    from langsmith import Client
    from langsmith.run_helpers import tracing_context
    from langsmith.schemas import Example
    module = evaluation()
    example = Example(id=uuid4(), dataset_id=uuid4(), created_at=datetime.now(timezone.utc),
                      inputs={"message": "test"}, outputs=reference())
    client = Client(api_key="test", api_url="https://langsmith.test", auto_batch_tracing=False, info={"version": "test"})
    try:
        with tracing_context(enabled=False):
            result = list(client.evaluate(lambda inputs: output(), data=[example],
                evaluators=[module.planner_evaluator], upload_results=False, max_concurrency=0))
        scores = {score.key: score.score for score in result[0]["evaluation_results"]["results"]}
        assert scores["strict_success"] == 1.0
        assert scores["parameters_correct"] == 1.0
    finally:
        client.close(timeout=1)


def test_cli_rules_diagnostic_and_gate_exit_codes(tmp_path, capsys):
    from scripts.evaluate_agent import main
    case = {"id": "hello", "inputs": {"message": "你好"}, "outputs": {"allowed_sources": ["llm"],
        "tasks": [{"id": "greeting", "capability": "general.respond", "depends_on": []}]}}
    path, report_path = tmp_path / "cases.jsonl", tmp_path / "report.json"
    path.write_text(json.dumps(case), encoding="utf-8")
    assert main(["--rules", "--cases", str(path), "--output", str(report_path)]) == 1
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["scores"]["strict_success"] == 0
    assert report["metadata"]["rules_baseline"] is True
    assert main(["--rules", "--cases", str(path), "--min-score", "0"]) == 0
    capsys.readouterr()


def test_invalid_actual_intents_fail_scoring_without_crashing():
    actual = output()
    actual["tasks"][1]["trade_intent"] = [42]
    assert evaluation().planner_scores(actual, reference())["strict_success"] == 0


def test_failed_or_missing_retrieval_never_receives_a_positive_score():
    module = evaluation()
    assert module.rag_scores({"error": "TimeoutError", "actual_ids": ["right"]},
        {"expected_ids": ["right"]})["hit_rate_at_k"] == 0
    assert module.rag_scores({}, {"expected_ids": [], "should_reject": True})["rejection_success"] == 0


def test_dataset_sync_rejects_unexpected_remote_case_before_running():
    module = evaluation()
    class Client:
        def has_dataset(self, **kwargs):
            return True
        def read_dataset(self, **kwargs):
            return SimpleNamespace(id=uuid4())
        def list_examples(self, **kwargs):
            return iter([SimpleNamespace(id=uuid4())])
        def create_examples(self, **kwargs):
            pytest.fail("must reject altered version before inserting or evaluating")
    with pytest.raises(ValueError, match="unexpected"):
        module.sync_dataset(Client(), "prefix", "planner", [
            {"id": "wanted", "inputs": {"message": "hello"}, "outputs": reference()}])


def test_disabled_rag_backend_is_an_error_not_a_correct_rejection():
    from app.core.config import Settings
    module = evaluation()
    target = module.make_target("rag", Settings(es_enabled=False), publish=False)
    actual = target({"agent": "support", "query": "unrelated question"})
    assert actual.get("error")
    assert module.rag_scores(actual, {"expected_ids": [], "should_reject": True}) == {"rejection_success": 0.0}


@pytest.mark.parametrize("fault", ["elasticsearch", "vector", "missing_rerank_key", "rerank_provider"])
def test_rag_evaluation_fails_on_backend_outage_instead_of_silent_fallback(monkeypatch, fault):
    from app.core.config import Settings
    from app.rag.embedding import RagEmbeddingService
    from app.rag.reranker import RagReranker
    from app.search import search_service
    module = evaluation()
    class ES:
        def search(self, **kwargs):
            if fault == "elasticsearch":
                raise ConnectionError("ES unavailable")
            if fault in {"missing_rerank_key", "rerank_provider"}:
                return {"hits": {"hits": [{"_id": "rule-1", "_score": 1, "_index": "rules", "_source":
                    {"id": "rule-1", "title": "退款", "content": "退款政策", "doc_type": "rule"}}]}}
            return {"hits": {"hits": []}}
    monkeypatch.setattr(search_service, "get_es_client", lambda: ES())
    def unavailable(*args, **kwargs):
        import httpx
        raise httpx.ConnectError("provider unavailable")
    monkeypatch.setattr(RagEmbeddingService, "embed_query", unavailable)
    monkeypatch.setattr(RagReranker, "_post", unavailable)
    config = Settings(es_enabled=True, rag_hybrid_enabled=fault == "vector", milvus_enabled=fault == "vector",
                      rag_rerank_enabled=fault in {"missing_rerank_key", "rerank_provider"},
                      rag_rerank_api_key="test" if fault == "rerank_provider" else "")
    actual = module.make_target("rag", config, publish=False)({"agent": "support", "query": "退款"})
    assert actual.get("error"), actual
    assert module.rag_scores(actual, {"expected_ids": [], "should_reject": True})["rejection_success"] == 0


@pytest.mark.parametrize("backend_status,should_error", [({}, False), ({"timed_out": True}, True), ({"_shards": {"failed": 1}}, True)])
def test_strict_retrieval_distinguishes_healthy_empty_results_from_partial_search(monkeypatch, backend_status, should_error):
    from app.core.config import Settings
    from app.search import search_service
    module = evaluation()
    class ES:
        def search(self, **kwargs):
            assert kwargs["ignore_unavailable"] is False
            assert kwargs["allow_no_indices"] is False
            return {"hits": {"hits": []}, **backend_status}
    monkeypatch.setattr(search_service, "get_es_client", lambda: ES())
    actual = module.make_target("rag", Settings(es_enabled=True), publish=False)({"agent": "support", "query": "unrelated"})
    assert bool(actual.get("error")) is should_error
    assert module.rag_scores(actual, {"expected_ids": [], "should_reject": True})["rejection_success"] == (0 if should_error else 1)

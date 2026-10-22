import importlib

from app.core.config import Settings


def module():
    return importlib.import_module("evaluation.semantic_evaluation")


def sample(category="policy"):
    return {"case_id": "policy-example", "trial": 1, "category": category, "answer": "所有订单均可无限期退款。",
            "source_error": None, "source_fallback": False, "source_generation_skipped": False,
            "question": "核销后能退款吗？", "rules": [{"title": "退款", "content": "已核销不自动退款，需要人工审核。"}],
            "gold_authority": "已核销不自动退款，需要人工审核。"}


def judge_result(**changes):
    return {"factual_support": 1, "completeness": 2, "clarity": 4, "relevance_current_constraints": None,
            "reasons": "The answer contradicts the supplied refund rule.",
            "evidence": ["已核销不自动退款"], "unsupported_claims": ["所有订单均可无限期退款"], **changes}


def test_unsupported_claim_is_recorded_as_advisory_failure(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage: judge_result())
    result = evaluator.run_judgment(sample(), Settings())
    assert result["status"] == "evaluated"
    assert result["scores"]["factual_support"] == 1
    assert result["advisory_supported"] is False
    assert result["unsupported_claims"] == ["所有订单均可无限期退款"]
    assert result["advisory_only"] is True


def test_empty_and_failed_source_answers_are_unavailable_not_unsafe(monkeypatch):
    evaluator = module()
    def forbidden(*args):
        raise AssertionError("Judge should not be called")
    monkeypatch.setattr(evaluator, "_invoke_judge", forbidden)
    for change in ({"answer": ""}, {"source_error": "ConnectionError"}):
        result = evaluator.run_judgment({**sample(), **change}, Settings())
        assert result["status"] == "source_unavailable"
        assert result["advisory_supported"] is None
        assert all(value is None for value in result["scores"].values())


def test_template_and_non_model_refusals_remain_judgeable_with_provenance(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage:
                        judge_result(factual_support=5, completeness=5, unsupported_claims=[]))
    for changes in ({"source_fallback": True}, {"source_generation_skipped": True}):
        result = evaluator.run_judgment({**sample(), **changes}, Settings())
        assert result["status"] == "evaluated"
        assert result["source_model_generated"] is False
        assert result["advisory_supported"] is True


def test_invalid_judge_never_gets_a_fallback_success(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage: judge_result(factual_support=True))
    result = evaluator.run_judgment(sample(), Settings())
    assert result["status"] == "judge_unavailable"
    assert result["error"] == "ValidationError"
    assert result["advisory_supported"] is None


def test_judge_backend_error_exposes_only_type(monkeypatch):
    evaluator = module()
    def fail(*args):
        raise RuntimeError("Bearer private-key")
    monkeypatch.setattr(evaluator, "_invoke_judge", fail)
    result = evaluator.run_judgment(sample(), Settings())
    assert result["error"] == "RuntimeError"
    assert "private-key" not in str(result)


def test_recommendation_grounding_is_explicitly_not_applicable(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage:
                        judge_result(factual_support=None, completeness=None, relevance_current_constraints=4,
                                     unsupported_claims=[]))
    result = evaluator.run_judgment(sample("current_preference"), Settings())
    assert result["scores"]["factual_support"] is None
    assert result["scores"]["relevance_current_constraints"] == 4
    assert result["grounding_applicability"] == "not_applicable_missing_candidate_facts"
    assert result["advisory_supported"] is None


def test_judge_cannot_infer_recommendation_unsafe_facts_without_snapshot(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage:
                        judge_result(factual_support=None, completeness=None, relevance_current_constraints=4))
    result = evaluator.run_judgment(sample("current_preference"), Settings())
    assert result["status"] == "judge_unavailable"
    assert result["unsupported_claims"] == []


def test_missing_policy_score_is_invalid_not_excluded_from_denominator(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage: judge_result(factual_support=None))
    result = evaluator.run_judgment(sample(), Settings())
    report = evaluator.summarize_semantics([result])
    assert result["status"] == "judge_unavailable"
    assert report["metrics"]["factual_support"]["eligible"] == 1
    assert report["metrics"]["factual_support"]["observed"] == 0
    assert report["metrics"]["factual_support"]["mean_observed"] is None


def test_preparation_requires_final_report_and_joins_independent_rule_prose():
    evaluator = module()
    source = {"final": False, "results": []}
    try:
        evaluator.prepare_cases(source, Settings())
    except ValueError:
        pass
    else:
        assert False, "An in-progress source must not be judged"
    source = {"final": True, "results": [{"id": "answer-refund-window", "trial": 1, "category": "policy",
              "outputs": {"evidence": {"answer": "退款需满足条件", "references": [{"title": "退款申请时效规则", "source": "food_rule_index"}],
                                         "rag_mode": "support_agent_hybrid_llm"}}}]}
    cases = evaluator.prepare_cases(source, Settings())
    inputs = cases[0]["inputs"]
    assert "已核销" in inputs["gold_authority"]
    assert inputs["rules"][0]["title"] == "退款申请时效规则"
    assert "人工审核" in inputs["rules"][0]["content"]
    assert "strict_scores" not in inputs


def test_summary_keeps_missing_and_not_applicable_denominators_separate(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_invoke_judge", lambda inputs, settings, usage: judge_result())
    rows = [evaluator.run_judgment(sample(), Settings()), evaluator.run_judgment({**sample(), "answer": ""}, Settings()),
            evaluator.run_judgment({**sample("current_preference"), "answer": ""}, Settings())]
    report = evaluator.summarize_semantics(rows)
    assert report["metrics"]["factual_support"] == {"eligible": 2, "observed": 1, "missing": 1, "not_applicable": 1, "mean_observed": 1.0}
    assert report["unsafe_answer_claims"] == 1
    assert report["source_unavailable"] == 2

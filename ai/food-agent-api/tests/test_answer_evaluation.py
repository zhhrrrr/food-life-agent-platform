"""The gold policy is hand specified; model text/citation IDs cannot set truth."""
import importlib

import pytest

from app.core.config import Settings


def module():
    return importlib.import_module("evaluation.answer_evaluation")


def refund_case():
    return next(c for c in module().load_cases() if c["id"] == "answer-refund-window")


def observed(answer="未核销且未超过退款时效可以申请；已核销或风控争议需人工审核。"):
    return {
        "result": {
            "response": answer, "rag_mode": "support_agent_hybrid_llm",
            "query": "退款", "rewritten_query": "退款", "capabilities": ["refund_rule"],
            "references": [{"id": "anything", "source": "food_rule_index", "title": "退款申请时效规则"}],
            "context": [{"id": "anything", "source": "food_rule_index", "title": "退款申请时效规则",
                         "content": "用户支付成功后，如套餐未核销且未超过活动或套餐规定的退款时效，可以发起退款申请。已核销、已过期且规则明确不可退、或存在风控争议的订单不自动退款，需要进入人工审核。",
                         "metadata": {"doc_type": "platform_rule", "source_type": "seeded_rule_document"}}],
            "session_constraints": {},
        },
        "retrieval": {"completed": True, "calls": 1, "mode": "hybrid", "hit_count": 1},
        "model": {"calls": 2, "completed": 2, "answer_completed": 1, "errors": [], "usage": {}},
    }


@pytest.mark.parametrize("answer", ["不管是否核销，订单都能退款。", "退款随时可申请，无需审核。", "", "   "])
def test_wrong_facts_and_empty_answers_cannot_pass(answer):
    checks = module().evaluate_observation(refund_case(), observed(answer))
    assert checks["answer_requirements"] is False
    assert not all(checks.values())


def test_template_cannot_masquerade_as_real_model_answer():
    observation = observed()
    observation["result"]["rag_mode"] = "support_agent_hybrid_template"
    checks = module().evaluate_observation(refund_case(), observation)
    assert checks["real_answer_model"] is False


def test_rewrite_completion_does_not_prove_answer_model_completed():
    observation = observed()
    observation["model"]["answer_completed"] = 0
    assert module().evaluate_observation(refund_case(), observation)["real_answer_model"] is False


def test_keyword_coverage_does_not_accept_explicitly_inverted_policy():
    checks = module().evaluate_observation(refund_case(), observed("未核销且未超过退款时效也不能退款，不需要人工审核。"))
    assert checks["no_forbidden_claims"] is False


def test_negative_sla_guarantee_is_an_acceptable_abstention():
    case = next(c for c in module().load_cases() if c["id"] == "answer-unknown-refund-sla")
    checks = module().evaluate_observation(case, observed("资料未明确到账天数，不能保证3个工作日到账。"))
    assert checks["no_forbidden_claims"] is True
    assert checks["answer_requirements"] is True


def test_arbitrary_valid_citation_id_does_not_establish_grounding():
    observation = observed()
    observation["result"]["context"][0]["content"] = "所有订单都可以无限期退款。"
    checks = module().evaluate_observation(refund_case(), observation)
    assert checks["evidence_grounding"] is False


def test_correct_gold_facts_with_authoritative_content_pass():
    assert all(module().evaluate_observation(refund_case(), observed()).values())


def test_evidence_export_error_preserves_observed_unsafe_answer(monkeypatch):
    evaluator = module()
    monkeypatch.setattr(evaluator, "_execute_chain", lambda *args: observed("退款随时可申请，无需审核。"))
    def fail(*args):
        raise RuntimeError("evidence export failed")
    monkeypatch.setattr(evaluator, "_safe_evidence", fail)
    result = evaluator.run_case(refund_case()["inputs"], Settings())
    assert result["error"] == "RuntimeError"
    assert result["checks"]["nonempty_answer"] is True
    assert result["checks"]["no_forbidden_claims"] is False
    assert "no_forbidden_claims" not in result.get("unverified_checks", [])


def test_backend_failure_does_not_pass_insufficient_evidence_case():
    case = next(c for c in module().load_cases() if c["id"] == "answer-unknown-refund-sla")
    observation = observed("当前资料未明确到账天数，需要向客服核实。")
    observation["retrieval"] = {"completed": False, "calls": 1}
    checks = module().evaluate_observation(case, observation)
    assert checks["retrieval_succeeded"] is False
    assert not all(checks.values())


def test_wrong_price_cannot_pass_using_a_valid_named_candidate():
    case = next(c for c in module().load_cases() if c["id"] == "answer-current-cuisine")
    observation = observed("粤菜推荐粤味楼，价格99元。")
    observation["result"]["context"][0].update(id="package-1", title="粤味楼双人餐", content="粤味楼双人餐",
        metadata={"shopName": "粤味楼", "price": "66.88 CNY", "factSource": "business_api", "factStatus": "success"})
    observation["result"]["references"][0]["id"] = "package-1"
    observation["result"]["session_constraints"] = {"cuisines": ["粤菜"]}
    assert module().evaluate_observation(case, observation)["evidence_grounding"] is False
    observation["result"]["response"] = "粤菜推荐粤味楼，价格66.88元。"
    assert all(module().evaluate_observation(case, observation).values())


def test_per_person_price_is_not_confused_with_package_total():
    cited = [
        {"id": "shop-1", "title": "粤味楼", "content": "餐厅", "metadata": {"avgPrice": "58.00 CNY"}},
        {"id": "package-1", "title": "粤味楼双人餐", "content": "套餐",
         "metadata": {"shopName": "粤味楼", "price": "43.78 CNY", "factSource": "business_api", "factStatus": "success"}},
    ]
    check = module()._recommendation_facts
    assert check("推荐粤味楼，人均价格为58元，套餐价格43.78元，符合人均80元以内预算。", cited)
    assert not check("推荐粤味楼，人均价格为43.78元。", cited)
    assert not check("推荐粤味楼，套餐价格58元。", cited)


def test_old_budget_cannot_pass_current_preference_check():
    case = next(c for c in module().load_cases() if c["id"] == "answer-current-budget")
    observation = observed("推荐人均80元餐厅。")
    observation["result"]["session_constraints"] = {"budget": {"max_yuan": 200, "budget_type": "per_person"}}
    assert module().evaluate_observation(case, observation)["current_constraints"] is False


def test_current_cuisine_does_not_allow_old_profile_cuisine_to_leak():
    case = next(c for c in module().load_cases() if c["id"] == "answer-current-cuisine")
    observation = observed("推荐粤菜。")
    observation["result"]["session_constraints"] = {"cuisines": ["粤菜", "川菜"]}
    assert module().evaluate_observation(case, observation)["current_constraints"] is False


def test_partial_execution_errors_preserve_observed_tokens_without_passing(monkeypatch):
    evaluator = module()
    observation = observed()
    observation["error"] = "ConnectionError"
    observation["model"]["usage"] = {"input_tokens": 123}
    monkeypatch.setattr(evaluator, "_execute_chain", lambda *args: observation)
    result = evaluator.run_case(refund_case()["inputs"], Settings())
    assert result["error"] == "ConnectionError"
    assert not any(result["checks"].values())
    assert result["evidence"]["model"]["usage"] == {"input_tokens": 123}


def test_runner_exception_has_all_false_checks_and_only_error_type(monkeypatch):
    evaluator = module()
    def fail(*args, **kwargs):
        raise RuntimeError("Bearer private-key and private-user-id")
    monkeypatch.setattr(evaluator, "_execute_chain", fail)
    result = evaluator.run_case(refund_case()["inputs"], Settings())
    assert result["error"] == "RuntimeError"
    assert set(result["checks"]) == set(refund_case()["outputs"]["required_checks"])
    assert not any(result["checks"].values())
    assert "private" not in str(result)


def test_runner_uses_frozen_inputs_and_redacts_diagnostic_ids(monkeypatch):
    evaluator = module()
    observation = observed()
    observation["result"]["response"] += " Bearer sensitive"
    observation["result"]["context"][0]["metadata"]["user_id"] = "private-user"
    monkeypatch.setattr(evaluator, "_execute_chain", lambda *args: observation)
    result = evaluator.run_case(refund_case()["inputs"], Settings())
    assert "private-user" not in str(result)
    assert "sensitive" not in str(result)
    assert "anything" not in str(result["evidence"])
    tampered = dict(refund_case()["inputs"], message="Different question")
    assert evaluator.run_case(tampered, Settings())["error"] == "ValueError"


def test_cases_define_all_exact_boolean_checks_without_mutable_cache():
    evaluator = module()
    cases = evaluator.load_cases()
    assert 12 <= len(cases) <= 18
    assert len({c["id"] for c in cases}) == len(cases)
    for case in cases:
        assert case["inputs"]["case_id"] == case["id"]
        assert set(case["outputs"].get("safety_checks", [])) <= set(case["outputs"]["required_checks"])
        assert set(evaluator.evaluate_observation(case, observed())) == set(case["outputs"]["required_checks"])
    cases[0]["inputs"]["message"] = "mutated"
    assert evaluator.load_cases()[0]["inputs"]["message"] != "mutated"


def test_model_observer_retains_provider_usage_but_never_generations():
    from langchain_core.messages import AIMessage
    from langchain_core.outputs import ChatGeneration, LLMResult
    evidence = {"calls": 0, "completed": 0, "answer_completed": 0, "errors": [], "usage": {}}
    callback = module()._ModelEvidence(evidence, answer=True)
    callback.on_chat_model_start({}, [["private prompt"]])
    callback.on_llm_end(LLMResult(generations=[[ChatGeneration(message=AIMessage(content="private answer",
        usage_metadata={"input_tokens": 12, "output_tokens": 8, "total_tokens": 20}))]]))
    assert evidence == {"calls": 1, "completed": 1, "answer_completed": 1, "errors": [],
                        "usage": {"input_tokens": 12, "output_tokens": 8, "total_tokens": 20}}


def test_tool_observer_reports_actual_attempts_without_arguments_or_ids():
    from app.tools.models import ToolCall, ToolContext, ToolExecution
    call = ToolCall(name="trade_get_package_snapshots", arguments={"package_ids": [123456]})
    class ExternalExecutor:
        def execute(self, calls, context):
            return [ToolExecution(call, "success", result={"private": "secret"}, attempts=2, elapsed_ms=17.5, used_fallback=True)]
    evidence = []
    module()._ObservedToolExecutor(ExternalExecutor(), evidence).execute([call], ToolContext())
    assert evidence == [{"name": "trade_get_package_snapshots", "status": "success", "attempts": 2,
                         "duration_ms": 17.5, "used_fallback": True, "error_code": ""}]
    assert "123456" not in str(evidence)

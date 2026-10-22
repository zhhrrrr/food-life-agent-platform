import importlib

import pytest


def module():
    assert importlib.util.find_spec("evaluation.live_evaluation") is not None, "Live-suite evaluator is missing"
    return importlib.import_module("evaluation.live_evaluation")


def test_order_observations_include_later_pages_for_cleanup():
    context = module().LiveContext()
    pages = [
        {"orders": [{"orderId": 1, "orderStatus": "CANCELED"}], "hasMore": True, "lastId": 1},
        {"orders": [{"orderId": 99, "orderStatus": "WAIT_PAY"}], "hasMore": False, "lastId": 99},
    ]
    context.java = lambda *args, **kwargs: pages.pop(0)
    try:
        assert set(context.orders()) == {1, 99}
    finally:
        context.http.close()


def test_live_read_requires_authoritative_package_reference_and_values():
    response = {"execution_status": "completed", "response": "套餐1价格18元，库存10份",
                "references": [{"id": "package:1", "source": "java_gateway_api"}]}
    snapshot = {"packageId": 1, "price": 1800, "stock": 10}
    assert all(module().read_checks(response, snapshot).values())
    response["references"] = [{"id": "order:1", "source": "java_gateway_api"}]
    assert not module().read_checks(response, snapshot)["correct_live_source"]
    response["references"] = [{"id": "package:1", "source": "java_gateway_api"}]
    response["response"] = "套餐1价格99元，库存10份"
    assert not module().read_checks(response, snapshot)["price_consistent"]


def test_preference_checks_require_actual_group_option_and_explicit_false():
    assert not module().preference_checks({"execution_status": "completed", "purchase_options": []})["waiting_preference_preserved"]
    response = {"execution_status": "completed", "purchase_options": [{"trade_type": "GROUP_BUY", "matches_preferences": None}]}
    assert not module().preference_checks(response)["waiting_preference_preserved"]
    response["purchase_options"][0]["matches_preferences"] = False
    assert module().preference_checks(response)["waiting_preference_preserved"]


def test_historical_correct_numbers_cannot_hide_wrong_current_facts():
    response = {"execution_status": "completed", "response": "套餐1现在价格99元（此前18元），库存999，之前剩余10份",
                "references": [{"id": "package:1", "source": "java_gateway_api"}]}
    checks = module().read_checks(response, {"packageId": 1, "price": 1800, "stock": 10})
    assert checks["price_consistent"] is False
    assert checks["stock_consistent"] is False


def test_live_dataset_has_only_declared_checks_and_unique_ids():
    cases = module().load_cases()
    assert len(cases) >= 10
    assert len({case["id"] for case in cases}) == len(cases)
    for case in cases:
        assert case["inputs"]["case_id"] == case["id"]
        assert set(case["outputs"].get("safety_checks", [])).issubset(case["outputs"]["required_checks"])


@pytest.mark.parametrize("failure_stage", ["cleanup", "http_close"])
def test_live_cleanup_error_preserves_observed_unexpected_write(monkeypatch, failure_stage):
    from types import SimpleNamespace
    evaluator = module()
    def close():
        if failure_stage == "http_close":
            raise RuntimeError("close failed after observation")
    class Context:
        def __init__(self):
            self.snapshot = {"packageId": 1, "price": 1800, "stock": 10}
            self.requests = 0
            self.turns = []
            self.http = SimpleNamespace(close=close)
        def initialize(self, **kwargs):
            return None
        def turn(self, message):
            return {"execution_status": "completed", "response": "套餐1价格18元，库存10份",
                    "references": [{"id": "package:1", "source": "java_gateway_api"}]}
        def new_orders(self):
            return {42: {"orderStatus": "WAIT_PAY"}}
        def cleanup(self):
            if failure_stage == "cleanup":
                raise RuntimeError("cleanup failed after observation")
            return {"cleanup_complete": True, "stock_restored": True}, [], []
    monkeypatch.setattr(evaluator, "LiveContext", Context)
    result = evaluator.run_case({"case_id": "live-package-facts", "message": "查询套餐1当前库存和价格"}, None)
    assert result["error"] == "RuntimeError"
    assert result["checks"]["no_unexpected_orders"] is False
    assert result["checks"]["cleanup_complete"] is False
    assert "no_unexpected_orders" not in result.get("unverified_checks", [])

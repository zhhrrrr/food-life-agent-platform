import json
from unittest.mock import Mock

from app.core.config import Settings
from app.memory.conversation import select_conversation
from app.memory.entities import EntityCandidate, EntityGroup, displayed_entity_groups
from app.memory.query_context import build_query_context
from app.memory.session_memory import (
    AgentMemoryClient,
    SessionContext,
    SessionMemoryService,
    SessionTurn,
    _session_context,
)
from app.orchestration.models import AgentTask, ExecutionPlan
from app.orchestration.registry import CapabilitySpec
from app.orchestration.service import OrchestrationService
from app.recommend.chain import RecommendChain
from app.trade.chain import TradeChain
from app.trade.gateway_client import GatewayQueryResult


def test_general_cancellation_policy_does_not_require_a_specific_order():
    selection = select_conversation("未支付订单可以取消吗？取消成功以后还能继续支付吗，库存和券怎么办？", [])
    assert not selection.clarification
    assert not selection.resolved


def test_empty_recommendations_explain_current_budget_and_unverified_allergy():
    class EmptyRetriever:
        def retrieve(self, *args, **kwargs):
            return type("Result", (), {"has_hits": False, "mode": "empty"})()

    result = RecommendChain(Settings(llm_router_enabled=False), retriever=EmptyRetriever()).run(
        "推荐人均80元以内的餐厅。我对花生过敏。", memory_context={"profile": {"allergy": ["花生"]}})
    for text in ("人均", "80", "花生", "交叉接触", "核验"):
        assert text in result.response
    assert "同步" not in result.response


def candidates(*ids, kind="package", source="recommend"):
    return EntityGroup(
        entity_type=kind,
        source=source,
        candidates=[EntityCandidate(id=str(i), label=f"候选{i}") for i in ids],
    )


def turn(*groups, user="推荐套餐", name="list"):
    return SessionTurn(
        turn_id=name,
        user=user,
        assistant="推荐结果",
        intent="recommend",
        entity_groups=list(groups),
        entities_recorded=True,
    )


def filler(n):
    return [
        SessionTurn(
            turn_id=f"f{i}", user=f"说明{i}", intent="support", entities_recorded=True
        )
        for i in range(n)
    ]


def test_default_context_contains_exactly_two_turns_and_summary_without_query_pollution():
    history = [SessionTurn(user="早期条件", intent="summary"), *filler(8)]
    query = build_query_context("推荐粤菜餐厅", history)
    payload = json.loads(query.history_prompt)
    assert len(payload["recent_turns"]) == 2
    assert payload["summary"] == "早期条件"
    assert query.retrieval_query == "推荐粤菜餐厅"


def test_reference_expansion_stops_at_nearest_matching_list():
    history = [
        turn(candidates(99, 88), name="old"),
        turn(candidates(22, 11), name="latest"),
        *filler(3),
    ]
    selected = select_conversation("第二个库存呢", history)
    assert selected.resolved == {"package": "11"}
    assert selected.source_turn_id == "latest"
    assert len(selected.history) == 4
    assert selected.expansion_reason == "reference"
    # Planner and workers keep the same selected window after the ID is bound.
    child = build_query_context(
        selected.resolve_message("第二个库存呢"), selected.history
    )
    assert len(json.loads(child.history_prompt)["recent_turns"]) == 4


def test_reference_lookup_never_exceeds_eight_turns_or_uses_summary_ids():
    history = [
        SessionTurn(user="套餐ID：77", intent="summary"),
        turn(candidates(22, 11)),
        *filler(8),
    ]
    selected = select_conversation("第二个库存呢", history)
    assert selected.resolved == {}
    assert selected.clarification
    assert len([t for t in selected.history if t.intent != "summary"]) == 8


def test_out_of_range_latest_list_does_not_fall_back_to_an_older_list():
    selected = select_conversation(
        "第三个库存呢", [turn(candidates(1, 2, 3)), turn(candidates(9, 8))]
    )
    assert selected.clarification and not selected.resolved


def test_separate_lists_and_entity_types_are_not_merged():
    selected = select_conversation(
        "第二个库存呢", [turn(candidates(1, 2), candidates(3, 4, source="other"))]
    )
    assert selected.clarification and not selected.resolved
    selected = select_conversation(
        "第二家环境呢", [turn(candidates(1, 2), candidates(8, 9, kind="shop"))]
    )
    assert selected.resolved == {"shop": "9"}
    selected = select_conversation(
        "那个呢", [turn(candidates(1), candidates(8, kind="shop"))]
    )
    assert selected.clarification


def test_explicit_ids_and_names_override_old_references():
    history = [turn(candidates(1, 2))]
    assert select_conversation("第二个库存呢，套餐ID：99", history).resolved == {
        "package": "99"
    }
    selected = select_conversation("那个库存呢，候选2", history)
    assert selected.resolved == {"package": "2"}


def test_unrecorded_generated_ids_and_user_questions_are_not_business_objects():
    selected = select_conversation(
        "这个库存呢",
        [SessionTurn(user="套餐ID：999", assistant="查询失败", entities_recorded=True)],
    )
    assert selected.clarification
    selected = select_conversation(
        "这个库存呢",
        [SessionTurn(user="推荐", assistant="套餐ID：999", entities_recorded=True)],
    )
    assert selected.clarification


def test_display_order_comes_from_visible_evidence_not_retrieval_rank_or_numeric_id():
    payload = {
        "response": "1. 乙套餐\n2. 甲套餐",
        "context": [
            {
                "id": "package-11",
                "metadata": {"packageId": 11, "packageName": "甲套餐"},
            },
            {
                "id": "package-99",
                "metadata": {"packageId": 99, "packageName": "乙套餐"},
            },
            {
                "id": "package-22",
                "metadata": {"packageId": 22, "packageName": "未展示套餐"},
            },
        ],
    }
    groups = displayed_entity_groups(payload)
    assert [c.id for c in groups[0].candidates] == ["99", "11"]


def test_introductory_mentions_do_not_override_the_actual_numbered_list():
    payload = {
        "response": "比较过甲套餐之后，推荐：\n1. 乙套餐\n2. 甲套餐",
        "context": [
            {"id": "package-11", "metadata": {"packageName": "甲套餐"}},
            {"id": "package-99", "metadata": {"packageName": "乙套餐"}},
        ],
    }
    group = displayed_entity_groups(payload)[0]
    assert group.ordinal_safe and [c.id for c in group.candidates] == ["99", "11"]
    payload["response"] = "甲套餐和乙套餐各有特点。"
    assert not displayed_entity_groups(payload)[0].ordinal_safe


def test_ambiguous_trade_references_do_not_call_tools():
    gateway = Mock()
    chain = TradeChain(Settings(llm_router_enabled=False), gateway_client=gateway)
    result = chain.run("那个库存呢", session_history=[turn(candidates(1, 2))])
    assert result.rag_mode == "reference_clarification"
    assert not gateway.mock_calls


def test_upstream_unordered_candidates_are_not_treated_as_a_display_list():
    gateway = Mock()
    chain = TradeChain(Settings(llm_router_enabled=False), gateway_client=gateway)
    result = chain.run(
        "第二个库存呢", upstream_artifacts={"r": {"package_ids": [99, 11]}}
    )
    assert result.rag_mode == "reference_clarification"
    assert not gateway.mock_calls


def test_a_corrected_explicit_id_is_used_for_the_actual_business_query():
    gateway = Mock()
    gateway.get_package_snapshot.return_value = GatewayQueryResult(
        capability="package_snapshot",
        status="success",
        data={"packageId": 2, "stock": 6},
    )
    chain = TradeChain(Settings(llm_router_enabled=False), gateway_client=gateway)
    chain.run("套餐99库存，不对，套餐2库存")
    gateway.get_package_snapshot.assert_called_once_with(2)


def test_multiple_references_and_large_ordinals_never_silently_select_one_object():
    history = [turn(candidates(1))]
    assert select_conversation("第十二个库存呢", history).clarification
    assert select_conversation("第1000个库存呢", history).clarification
    assert select_conversation("第一个和第二个哪个好", history).clarification


def test_malformed_saved_constraints_are_ignored_instead_of_crashing_the_chain():
    history = [
        SessionTurn(user="推荐", session_constraints={"budget": {"max_yuan": "bad"}})
    ]
    assert history[0].session_constraints == {}
    query = build_query_context("还有其他餐厅吗", history)
    assert (
        RecommendChain(Settings(llm_router_enabled=False))
        .rewrite_query("还有其他餐厅吗", query)
        .budget
        is None
    )


def test_latest_budget_and_dietary_snapshot_yield_to_current_correction():
    history = [turn(user="人均预算100元"), turn(user="改成人均预算200元")]
    chain = RecommendChain(Settings(llm_router_enabled=False))
    query = build_query_context("还有其他餐厅吗", history)
    assert chain.rewrite_query("还有其他餐厅吗", query).budget.max_yuan == 200
    history[-1].session_constraints = {
        "budget": {"max_yuan": 200, "budget_type": "per_person"},
        "dietary_requirements": ["不辣"],
    }
    rewrite = chain.rewrite_query(
        "还有其他餐厅吗", build_query_context("还有其他餐厅吗", history)
    )
    assert rewrite.budget.max_yuan == 200 and rewrite.dietary_requirements == ["不辣"]
    rewrite = chain.rewrite_query(
        "改成人均预算300元", build_query_context("改成人均预算300元", history)
    )
    assert rewrite.budget.max_yuan == 300
    history[-1].session_constraints["budget"] = None
    rewrite = chain.rewrite_query(
        "还有其他餐厅吗", build_query_context("还有其他餐厅吗", history)
    )
    assert rewrite.budget is None


def test_missing_budget_and_explicit_recall_extend_to_the_matching_turn():
    history = [turn(user="人均预算100元"), *filler(4)]
    selected = select_conversation("还有其他餐厅吗", history)
    assert (
        len(selected.history) == 5 and selected.expansion_reason == "missing_constraint"
    )
    selected = select_conversation("最开始的预算是多少", history)
    assert len(selected.history) == 5 and selected.expansion_reason == "explicit_recall"


def test_new_topic_boundary_prevents_reusing_previous_candidate_ids():
    history = [
        turn(candidates(1, 2)),
        SessionTurn(user="换个话题", intent="general", entities_recorded=True),
    ]
    assert select_conversation("第二个库存呢", history).clarification
    history[0].session_constraints = {"budget": {"max_yuan": 100}}
    query = build_query_context("重新推荐粤菜", history)
    assert query.session_constraints == {} and query.retrieval_query == "重新推荐粤菜"


def test_entity_groups_and_constraints_round_trip_through_java_wire_format():
    wire = Mock()
    client = AgentMemoryClient(Settings(), client=wire)
    response = wire.request.return_value
    response.json.return_value = {"code": "0000", "data": {"sessionId": "s"}}
    groups = [candidates(99, 11).model_dump()]
    constraints = {"budget": {"max_yuan": 200, "budget_type": "per_person"}}
    client.append(
        "s",
        "token",
        turn_id="t",
        user="u",
        assistant="a",
        intent="recommend",
        run_id="r",
        entity_groups=groups,
        session_constraints=constraints,
    )
    structured = wire.request.call_args.kwargs["json"]["contextJson"]
    loaded = _session_context(
        {"turns": [{"user": "u", "contextJson": structured}]}, "s"
    )
    assert loaded.turns[0].entity_groups[0].candidates[1].id == "11"
    assert loaded.turns[0].session_constraints == constraints
    assert loaded.turns[0].entities_recorded


def test_summary_cursor_only_advances_over_evicted_turns():
    settings = Settings(session_memory_max_turns=2, session_summary_trigger_turns=2)
    java = Mock()
    java.update_summary.return_value = True
    memory = SessionMemoryService(settings, java_client=java)
    memory.summary_service.summarize = Mock(return_value="merged")
    context = SessionContext(
        session_id="s",
        version=5,
        turns=[SessionTurn(user=f"q{i}", sequence_no=i * 2) for i in range(1, 6)],
    )
    memory._maybe_summarize(context, "token")
    assert context.summary_through_seq == 6
    assert [t.sequence_no for t in context.history(2) if t.intent != "summary"] == [
        8,
        10,
    ]


def test_reference_clarification_survives_restart_and_binds_id_before_planning(
    tmp_path,
):
    seen = []

    class Planner:
        def plan(self, message, history=None):
            seen.append((message, history))
            return ExecutionPlan(
                run_id="fixed-run",
                goal=message,
                source="rules",
                reason="test",
                confidence=1,
                tasks=[
                    AgentTask(
                        id="read",
                        agent="trade",
                        capability="trade.query",
                        objective="查询套餐ID：999库存",
                    )
                ],
            )

    calls = []

    def read(state):
        calls.append(state)
        return {
            "response": "套餐ID：2，库存6",
            "facts": [{"data": {"packageId": 2, "stock": 6}}],
        }

    settings = Settings(
        llm_router_enabled=False,
        long_term_memory_enabled=False,
        session_memory_enabled=True,
        orchestration_checkpoint_path=str(tmp_path / "runs.sqlite"),
    )
    registry = {"trade.query": CapabilitySpec("trade", read)}
    first = OrchestrationService(settings, planner=Planner(), registry=registry)
    result = first.turn(
        user_id="7",
        authorization="token",
        session_id="s",
        request_id="one",
        message="那个库存呢",
        session_history=[turn(candidates(1, 2))],
    )
    assert (
        result["execution_status"] == "waiting_input"
        and result["interrupt"]["type"] == "reference"
    )
    assert not calls and not seen
    first.close()
    second = OrchestrationService(settings, planner=Planner(), registry=registry)
    try:
        result = second.turn(
            user_id="7",
            authorization="token",
            session_id="s",
            request_id="two",
            run_id=result["run_id"],
            message="套餐ID：2",
        )
        assert result["execution_status"] == "completed"
        assert len(calls) == 1
        assert "套餐ID：2" in calls[0]["message"] and "999" not in calls[0]["message"]
        assert "套餐ID：2" in seen[0][0]
        with second.database.connect() as db:
            payloads = [
                json.loads(row[0])
                for row in db.execute(
                    "SELECT payload FROM memory_jobs WHERE session_id='s'"
                ).fetchall()
            ]
        assert any(p.get("entity_groups") for p in payloads)
    finally:
        second.close()

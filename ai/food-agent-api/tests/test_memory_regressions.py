import json
from unittest.mock import Mock

import pytest

from app.core.config import Settings
from app.memory.delivery import MemoryDelivery
from app.memory.long_term_memory import UserMemoryVectorStore, utc_epoch
from app.memory.preferences import preference_updates
from app.memory.query_context import build_query_context
from app.memory.session_memory import (
    SessionContext,
    SessionMemoryService,
    SessionSummaryService,
    SessionTurn,
)
from app.orchestration.planner import _planner_messages
from app.recommend.chain import RecommendChain


def test_summary_survives_recent_windows_without_polluting_search():
    context = SessionContext(
        session_id="s",
        summary="EARLY_CONSTRAINT",
        turns=[
            SessionTurn(user=f"q{i}", assistant="a", sequence_no=i * 2)
            for i in range(8)
        ],
    )
    history = context.history(8)
    assert "EARLY_CONSTRAINT" in repr(_planner_messages("new topic", history))
    query = build_query_context("new topic", history)
    assert "EARLY_CONSTRAINT" in query.history_prompt
    assert query.retrieval_query == "new topic"


def test_failed_summary_does_not_advance_coverage(monkeypatch):
    monkeypatch.setattr(
        "app.memory.session_memory.invoke_structured_output",
        Mock(side_effect=RuntimeError("offline")),
    )
    java = Mock()
    context = SessionContext(
        session_id="s",
        summary="x" * 2000,
        summary_through_seq=2,
        turns=[
            SessionTurn(user="new", sequence_no=4),
            SessionTurn(user="critical", sequence_no=6),
            SessionTurn(user="recent", sequence_no=8),
        ],
    )
    settings = Settings(session_memory_max_turns=1, session_summary_trigger_turns=2)
    memory = SessionMemoryService(settings, java_client=java)
    memory._maybe_summarize(context, "token")
    assert context.summary_through_seq == 2
    java.update_summary.assert_not_called()


def test_summary_covers_only_new_messages(monkeypatch):
    invoke = Mock(return_value=type("Summary", (), {"summary": "merged"})())
    monkeypatch.setattr("app.memory.session_memory.invoke_structured_output", invoke)
    context = SessionContext(
        session_id="s",
        summary_through_seq=2,
        turns=[
            SessionTurn(user="OLD_UNIQUE", sequence_no=2),
            SessionTurn(user="NEW_UNIQUE", sequence_no=4),
            SessionTurn(user="RECENT_UNIQUE", sequence_no=6),
        ],
    )
    assert SessionSummaryService(Settings(session_memory_max_turns=1)).summarize(context) == "merged"
    prompt = repr(invoke.call_args.kwargs["messages"])
    assert "NEW_UNIQUE" in prompt and "OLD_UNIQUE" not in prompt
    assert "RECENT_UNIQUE" not in prompt


@pytest.mark.parametrize(
    "message", ["今天预算100元", "这次不要辣", "我朋友对花生过敏", "我喜欢川菜吗"]
)
def test_temporary_third_party_and_questions_are_not_profiles(message):
    assert preference_updates(message) == []


def test_explicit_preference_add_remove_delete_and_budget_scope():
    assert preference_updates("我对花生和海鲜过敏")[0].values == ["花生", "海鲜"]
    assert preference_updates("我不再对花生过敏")[0].operation == "remove"
    assert preference_updates("忘记我的预算")[0].operation == "delete"
    budget = preference_updates("记住我平时人均预算100元")[0]
    assert (budget.max_yuan, budget.budget_type) == (100, "per_person")


def test_structured_profile_defaults_yield_to_current_request():
    memory = {
        "profile": {
            "favorite": ["川菜"],
            "allergy": ["花生"],
            "budget": {"max_yuan": 100, "budget_type": "per_person"},
        }
    }
    chain = RecommendChain(Settings(llm_router_enabled=False))
    rewrite = chain.rewrite_query(
        "推荐粤菜", build_query_context("推荐粤菜", [], memory)
    )
    assert rewrite.cuisines == ["粤菜"]
    assert rewrite.budget.max_yuan == 100 and rewrite.budget.budget_type == "per_person"
    assert "过敏禁忌花生" in rewrite.dietary_requirements
    override = chain.rewrite_query(
        "推荐粤菜人均预算200元",
        build_query_context("推荐粤菜人均预算200元", [], memory),
    )
    assert override.budget.max_yuan == 200


def test_summary_budget_and_current_correction_are_effective_before_delivery():
    chain = RecommendChain(Settings(llm_router_enabled=False))
    history = SessionContext(
        session_id="s", summary="本次仍然有效的条件：人均预算100元"
    ).history(8)
    context = build_query_context("还有其他餐厅吗", history)
    assert chain.rewrite_query("还有其他餐厅吗", context).budget.max_yuan == 100
    corrected = build_query_context(
        "我不再对花生过敏，推荐餐厅", [], {"profile": {"allergy": ["花生", "海鲜"]}}
    )
    assert corrected.profile["allergy"] == ["海鲜"]


def test_context_budget_drops_optional_events_before_dietary_constraints():
    context = build_query_context(
        "还有吗",
        [SessionTurn(user="x" * 800, assistant="a" * 1200)],
        {
            "profile": {"allergy": ["花生"]},
            "events": [{"content": "e" * 1000} for _ in range(10)],
        },
        4000,
    )
    assert len(context.history_prompt) <= 4000
    assert json.loads(context.history_prompt)["memory"]["profile"]["allergy"] == [
        "花生"
    ]


def test_candidate_window_is_independent_of_prompt_top_k():
    client = Mock()
    client.has_collection.return_value = True
    client.search.return_value = [
        [
            {
                "distance": 1 - i / 100,
                "entity": {
                    "event_id": str(i),
                    "user_id": 7,
                    "event_type": "pref",
                    "content": "x",
                    "entity_id": "target" if i == 4 else "other",
                    "occurred_at": 1,
                    "expires_at": 9999999999,
                },
            }
            for i in range(1, 6)
        ]
    ]
    store = UserMemoryVectorStore(Settings(long_term_memory_top_k=3), client=client)
    candidates = store.search(7, [1.0], limit=10)
    assert len(candidates) == 5 and candidates[3].entity_id == "target"


def test_time_formats_have_identical_utc_meaning():
    assert utc_epoch([2026, 1, 1, 0, 0, 0]) == utc_epoch("2026-01-01T00:00:00")
    assert utc_epoch("2026-01-01T08:00:00+08:00") == utc_epoch("2026-01-01T00:00:00Z")


def test_delivery_survives_restart_and_deduplicates_without_tokens(
    tmp_path, monkeypatch
):
    settings = Settings(
        session_memory_enabled=True,
        session_summary_enabled=False,
        orchestration_checkpoint_path=str(tmp_path / "jobs.sqlite"),
    )
    first = MemoryDelivery(settings)
    first.enqueue(
        "7",
        "session",
        "turn",
        user="hello",
        assistant="ok",
        intent="general",
        run_id="run",
    )
    client = Mock()
    client.append.side_effect = RuntimeError("Java unavailable")
    monkeypatch.setattr("app.memory.delivery.AgentMemoryClient", lambda *a, **k: client)
    first.drain()
    with first.database.connect() as db:
        assert db.execute("SELECT status FROM memory_jobs").fetchone()[0] == "PENDING"
        db.execute("UPDATE memory_jobs SET next_retry=0")
    first.close()
    second = MemoryDelivery(settings)
    client.append.side_effect = None
    second.drain()
    second.enqueue(
        "7",
        "session",
        "turn",
        user="hello",
        assistant="ok",
        intent="general",
        run_id="run",
    )
    second.drain()
    assert client.append.call_count == 2
    assert client.append.call_args.kwargs["turn_id"] == "turn"
    with second.database.connect() as db:
        row = db.execute("SELECT status,payload FROM memory_jobs").fetchone()
        assert row == ("DONE", "{}")
    second.close()


def test_real_redis_rejects_late_old_snapshot():
    from uuid import uuid4

    from redis import Redis
    from redis.exceptions import RedisError

    client = Redis(
        host="127.0.0.1",
        socket_connect_timeout=0.3,
        socket_timeout=0.3,
        decode_responses=True,
    )
    try:
        client.ping()
    except (OSError, RedisError):
        pytest.skip("local Redis not available")
    prefix = "test:memory:" + uuid4().hex + ":"
    memory = SessionMemoryService(
        Settings(session_memory_enabled=True, session_memory_key_prefix=prefix),
        redis_client=client,
    )
    try:
        memory._cache_set(SessionContext(session_id="s", version=2), "7")
        memory._cache_set(SessionContext(session_id="s", version=1), "7")
        assert memory.load("s", user_id="7")[0].version == 2
    finally:
        client.delete(prefix + "7:s")
        client.close()

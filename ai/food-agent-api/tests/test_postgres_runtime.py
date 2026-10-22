"""Run with TEST_POSTGRES_URL against an empty, disposable database."""

import os
from uuid import uuid4

import pytest
from fastapi import HTTPException
from test_persistent_orchestration import Actions, FixedPlanner, Reads, workflow_task

from app.core.config import Settings
from app.orchestration.registry import CapabilitySpec, controlled_trade_node
from app.orchestration.service import OrchestrationService
from app.trade.service import TradeWorkflowService


@pytest.mark.skipif(
    not os.getenv("TEST_POSTGRES_URL"), reason="requires disposable PostgreSQL database"
)
def test_postgres_instances_restore_confirm_once_and_share_memory_journal(monkeypatch):
    config = Settings(
        runtime_database_url=os.environ["TEST_POSTGRES_URL"],
        session_memory_enabled=True,
    )
    actions, reads = Actions(), Reads()
    registry = {
        "trade.workflow": CapabilitySpec(
            "trade", controlled_trade_node, execution="workflow"
        )
    }

    def services():
        child = TradeWorkflowService(config, read_client=reads, action_client=actions)
        parent = OrchestrationService(
            config,
            planner=FixedPlanner([workflow_task()]),
            registry=registry,
            trade_service=child,
        )
        return parent, child

    session = uuid4().hex
    first, child = services()
    pending = first.turn(
        user_id="7",
        authorization="fake",
        session_id=session,
        request_id="start",
        message="buy",
    )
    assert pending["execution_status"] == "waiting_input"
    first.close()
    child.close()
    second, child = services()
    other, other_child = services()
    try:
        request = {
            "user_id": "7",
            "authorization": "fake",
            "session_id": session,
            "run_id": pending["run_id"],
            "task_id": pending["interrupt"]["task_id"],
            "request_id": "confirm",
            "approved": True,
            "confirmation_id": pending["trade"]["interrupt"]["confirmation_id"],
        }
        completed = second.turn(**request)
        assert completed["execution_status"] == "completed"
        assert other.turn(**request)["execution_status"] == "completed"
        assert len(actions.calls) == 1
        with other.db() as db:
            rows = db.execute(
                "SELECT payload FROM memory_jobs WHERE session_id=? AND kind='turn'",
                (session,),
            ).fetchall()
            assert len(rows) == 2
            assert "fake" not in repr(rows)
            assert any("trade_completed" in row[0] for row in rows)
        key = other.config("7", pending["run_id"], session)["configurable"]["thread_id"]
        from types import SimpleNamespace
        monkeypatch.setattr("app.trade.storage.time", SimpleNamespace(time=lambda: -1))
        with (
            second.storage.lease(key),
            pytest.raises(HTTPException),
            other.storage.lease(key),
        ):
            pass
    finally:
        second.close()
        child.close()
        other.close()
        other_child.close()


@pytest.mark.skipif(
    not os.getenv("TEST_POSTGRES_URL"), reason="requires disposable PostgreSQL database"
)
def test_sqlite_migration_preserves_pending_trade_and_request_receipts(tmp_path):
    from urllib.parse import urlencode

    import psycopg
    from psycopg import sql
    from test_persistent_orchestration import confirm, services, start

    from scripts.migrate_agent_state import migrate

    schema = "migration_" + uuid4().hex
    base = os.environ["TEST_POSTGRES_URL"]
    target = (
        base
        + ("&" if "?" in base else "?")
        + urlencode({"options": "-csearch_path=" + schema})
    )
    with psycopg.connect(base, autocommit=True) as db:
        db.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
    parent, child, reads, actions = services(tmp_path, [workflow_task()])
    pending = start(parent)
    parent.close()
    child.close()
    try:
        counts = migrate(
            [str(tmp_path / "agent.sqlite"), str(tmp_path / "trade.sqlite")], target
        )
        assert counts["checkpoints"] > 0
        config = Settings(runtime_database_url=target)
        child = TradeWorkflowService(config, read_client=reads, action_client=actions)
        parent = OrchestrationService(
            config,
            planner=FixedPlanner([workflow_task()]),
            registry={
                "trade.workflow": CapabilitySpec(
                    "trade", controlled_trade_node, execution="workflow"
                )
            },
            trade_service=child,
        )
        try:
            restored = parent.view("7", pending["run_id"], "session")
            assert (
                restored["trade"]["interrupt"]["confirmation_id"]
                == pending["trade"]["interrupt"]["confirmation_id"]
            )
            assert confirm(parent, restored)["execution_status"] == "completed"
            assert len(actions.calls) == 1
        finally:
            parent.close()
            child.close()
    finally:
        with psycopg.connect(base, autocommit=True) as db:
            db.execute(sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(schema)))

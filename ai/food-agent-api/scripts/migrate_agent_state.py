"""Offline SQLite -> PostgreSQL migration; stop Agent instances before running."""

import argparse
import os
import sqlite3
import sys
import tempfile
from collections import defaultdict
from contextlib import ExitStack, closing
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langgraph.checkpoint.sqlite import SqliteSaver

from app.core.persistence import RuntimeDatabase

TABLES = {
    "trade_receipts",
    "trade_requests",
    "agent_runs",
    "agent_run_events",
    "memory_jobs",
}


def migrate(sources: list[str], destination: str) -> dict[str, int]:
    target = RuntimeDatabase(destination)
    if not target.postgres:
        raise ValueError("RUNTIME_DATABASE_URL must name PostgreSQL")
    snapshots = []
    counts = defaultdict(int)
    try:
        with ExitStack() as stack:
            for source in sources:
                path = Path(source).resolve(strict=True)
                handle, name = tempfile.mkstemp(
                    prefix="food-memory-migration-", suffix=".sqlite"
                )
                os.close(handle)
                snapshots.append(Path(name))
                snapshot = stack.enter_context(closing(sqlite3.connect(name)))
                with closing(
                    sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)
                ) as original:
                    original.backup(snapshot)
                active = snapshot.execute(
                    "SELECT COUNT(*) FROM trade_leases WHERE expires>strftime('%s','now')"
                ).fetchone()[0]
                if active:
                    raise RuntimeError(
                        "source has active runs; stop Agent instances first"
                    )
            # Snapshot handles are kept in the ExitStack; reopening isolates saver cursors from SQL copying.
            readers = [
                SqliteSaver(stack.enter_context(closing(sqlite3.connect(str(path)))))
                for path in snapshots
            ]
            schemas = {}
            for reader in readers:
                for table, ddl in reader.conn.execute(
                    "SELECT name,sql FROM sqlite_master WHERE type='table'"
                ):
                    if table in TABLES:
                        schemas[table] = ddl.replace(
                            "CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ", 1
                        )
            saver = target.checkpointer()
            if next(saver.list(None, limit=1), None):
                raise RuntimeError(
                    "destination already has checkpoints; use an empty database/schema"
                )
            with target.connect() as db:
                for ddl in schemas.values():
                    db.executescript(ddl)
                if any(
                    db.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
                    for table in schemas
                ):
                    raise RuntimeError("destination already has execution data")
            for reader in readers:
                for item in reader.list(None):
                    configurable = dict(item.config["configurable"])
                    configurable["checkpoint_id"] = (
                        (item.parent_config or {})
                        .get("configurable", {})
                        .get("checkpoint_id")
                    )
                    saved = saver.put(
                        {"configurable": configurable},
                        item.checkpoint,
                        item.metadata,
                        item.checkpoint["channel_versions"],
                    )
                    writes = defaultdict(list)
                    for task_id, channel, value in item.pending_writes or []:
                        writes[task_id].append((channel, value))
                    for task_id, values in writes.items():
                        saver.put_writes(saved, values, task_id)
                    counts["checkpoints"] += 1
                existing = {
                    row[0]
                    for row in reader.conn.execute(
                        "SELECT name FROM sqlite_master WHERE type='table'"
                    )
                }
                with target.connect() as db:
                    for table in TABLES & existing:
                        columns = [
                            row[1]
                            for row in reader.conn.execute(
                                f'PRAGMA table_info("{table}")'
                            )
                        ]
                        names = ",".join('"' + name + '"' for name in columns)
                        sql = f'INSERT INTO "{table}" ({names}) VALUES ({",".join("?" for _ in columns)}) ON CONFLICT DO NOTHING'
                        for row in reader.conn.execute(f'SELECT * FROM "{table}"'):
                            counts[table] += db.execute(sql, row).rowcount
            if "agent_run_events" in schemas:
                with target.connect() as db:
                    db.execute(
                        "SELECT setval(pg_get_serial_sequence('agent_run_events','id'),COALESCE(MAX(id),1),MAX(id) IS NOT NULL) FROM agent_run_events"
                    )
        return dict(counts)
    finally:
        target.close()
        for path in snapshots:
            path.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+", help="Agent and Trade SQLite files")
    arguments = parser.parse_args()
    print(migrate(arguments.sources, os.environ.get("RUNTIME_DATABASE_URL", "")))

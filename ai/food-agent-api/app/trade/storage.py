"""Durable action receipts and cross-process thread leases beside graph checkpoints."""
from __future__ import annotations

import hashlib
import json
import threading
import time
from contextlib import contextmanager
from uuid import uuid4

from fastapi import HTTPException

from app.core.persistence import RuntimeDatabase
from app.trade.actions import ActionResult


class TradeStorage:
    def __init__(self, path: str, *, database=None):
        self.path = path
        self.database = database or RuntimeDatabase(path)
        with self.connect() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS trade_leases (thread_id TEXT PRIMARY KEY, token TEXT NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS trade_receipts (operation_id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, result TEXT);
                CREATE TABLE IF NOT EXISTS trade_requests (thread_id TEXT NOT NULL, request_id TEXT NOT NULL, fingerprint TEXT NOT NULL, response TEXT NOT NULL, PRIMARY KEY(thread_id, request_id));
            """)

    def connect(self):
        return self.database.connect()

    def now(self, db):
        return float(db.execute("SELECT EXTRACT(EPOCH FROM clock_timestamp())").fetchone()[0]) if self.database.postgres else time.time()

    @contextmanager
    def lease(self, thread_id: str):
        token = uuid4().hex
        with self.connect() as db:
            now = self.now(db)
            db.execute("DELETE FROM trade_leases WHERE thread_id=? AND expires<?", (thread_id, now))
            inserted = db.execute("INSERT INTO trade_leases VALUES (?,?,?) ON CONFLICT DO NOTHING", (thread_id, token, now + 45)).rowcount
        if not inserted:
            raise HTTPException(409, "交易工作流正在执行，请稍后重试。")
        stopping, lost = threading.Event(), threading.Event()

        def renew():
            while not stopping.wait(10):
                try:
                    with self.connect() as db:
                        updated = db.execute("UPDATE trade_leases SET expires=? WHERE thread_id=? AND token=?", (self.now(db) + 45, thread_id, token)).rowcount
                    if not updated:
                        lost.set()
                        return
                except Exception:  # noqa: BLE001 - any renewal failure invalidates the lease
                    lost.set()
                    return

        def ensure_owned():
            with self.connect() as db:
                current = db.execute("SELECT token,expires FROM trade_leases WHERE thread_id=?", (thread_id,)).fetchone()
                now = self.now(db)
            if lost.is_set() or not current or current[0] != token or current[1] <= now:
                raise HTTPException(409, "交易工作流锁已失效，请恢复同一工作流。")

        renewal = threading.Thread(target=renew, name="trade-thread-lease", daemon=True)
        renewal.start()
        try:
            yield ensure_owned
        finally:
            stopping.set()
            renewal.join(timeout=1)
            with self.connect() as db:
                db.execute("DELETE FROM trade_leases WHERE thread_id=? AND token=?", (thread_id, token))

    @staticmethod
    def fingerprint(value) -> str:
        return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()

    def replay(self, thread_id: str, request_id: str, payload: dict) -> dict | None:
        with self.connect() as db:
            row = db.execute("SELECT fingerprint,response FROM trade_requests WHERE thread_id=? AND request_id=?", (thread_id, request_id)).fetchone()
        if not row:
            return None
        if row[0] != self.fingerprint(payload):
            raise HTTPException(409, "同一 request_id 不能用于不同请求。")
        return json.loads(row[1]) if row[1] else None

    def reserve(self, thread_id: str, request_id: str, payload: dict):
        with self.connect() as db:
            db.execute("INSERT INTO trade_requests VALUES (?,?,?,?) ON CONFLICT DO NOTHING", (thread_id, request_id, self.fingerprint(payload), ""))

    def remember(self, thread_id: str, request_id: str, payload: dict, response: dict):
        with self.connect() as db:
            db.execute("UPDATE trade_requests SET response=? WHERE thread_id=? AND request_id=?", (json.dumps(response, ensure_ascii=False), thread_id, request_id))

    def execute(self, operation_id: str, identity: dict, handler, *, recover: bool = False) -> ActionResult:
        fingerprint = self.fingerprint(identity)
        with self.connect() as db:
            inserted = db.execute("INSERT INTO trade_receipts VALUES (?,?,NULL) ON CONFLICT DO NOTHING", (operation_id, fingerprint)).rowcount
            row = db.execute("SELECT fingerprint,result FROM trade_receipts WHERE operation_id=?", (operation_id,)).fetchone()
        if row[0] != fingerprint:
            raise HTTPException(409, "交易幂等键与已确认参数不一致。")
        if row[1]:
            result = ActionResult.model_validate_json(row[1])
            if result.status not in {"uncertain", "unauthorized"} or not recover:
                return result
        elif not inserted and not recover:
            return ActionResult(status="uncertain", message="此前操作可能已提交，请恢复同一工作流核对结果。")
        result = handler()
        with self.connect() as db:
            db.execute("UPDATE trade_receipts SET result=? WHERE operation_id=?", (result.model_dump_json(), operation_id))
        return result

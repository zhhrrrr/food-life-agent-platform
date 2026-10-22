"""Durable, token-free delivery. Uses the same SQL and leases as Agent runs."""

import json
import logging
import threading
from contextlib import nullcontext
from datetime import UTC, datetime, timedelta
from functools import lru_cache

from app.core.config import get_settings
from app.core.metrics import MEMORY_OPERATIONS
from app.core.persistence import RuntimeDatabase
from app.memory.long_term_memory import LongTermMemoryService
from app.memory.session_memory import (
    AgentMemoryClient,
    SessionMemoryService,
    memory_turn_id,
)
from app.trade.storage import TradeStorage

logger = logging.getLogger("food_agent.memory.delivery")


class MemoryDelivery:
    def __init__(self, settings, *, database=None):
        self.settings = settings
        self.database = database or RuntimeDatabase(
            settings.runtime_database_url or settings.orchestration_checkpoint_path
        )
        self.storage = TradeStorage(self.database.location, database=self.database)
        self.stopping = threading.Event()
        self.thread = None
        with self.database.connect() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS memory_jobs (
                  job_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, session_id TEXT NOT NULL,
                  kind TEXT NOT NULL, payload TEXT NOT NULL, created REAL NOT NULL,
                  status TEXT NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
                  next_retry REAL NOT NULL DEFAULT 0);
                CREATE INDEX IF NOT EXISTS idx_memory_jobs_pending ON memory_jobs(status,next_retry,created);
                CREATE INDEX IF NOT EXISTS idx_memory_jobs_user ON memory_jobs(user_id,kind,status,created,job_id);
            """)

    def enqueue(self, user_id, session_id, turn_id, *, db=None, **payload):
        if not user_id or not self.settings.session_memory_enabled:
            return
        args = (
            turn_id,
            str(user_id),
            session_id,
            "turn",
            json.dumps(payload, ensure_ascii=False),
        )
        sql = "INSERT INTO memory_jobs(job_id,user_id,session_id,kind,payload,created) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING"
        with self.database.connect() if db is None else nullcontext(db) as connection:
            connection.execute(sql, (*args, self.storage.now(connection)))

    def enqueue_result(self, user_id, session_id, request_id, message, result, *, db):
        if message.strip():
            turn_id = memory_turn_id(user_id, session_id, request_id, message)
            experience = None
            trade = result.get("trade") or {}
            if (
                trade.get("status") == "completed"
                and (trade.get("result") or {}).get("status") == "success"
            ):
                data = (trade.get("result") or {}).get("data") or {}
                now = datetime.now(UTC).replace(tzinfo=None)
                action = {
                    "create": "创建订单",
                    "pay": "发起支付",
                    "cancel": "取消订单",
                    "refund": "申请退款",
                    "sync_payment": "核对支付结果",
                }.get(trade.get("action"), "执行交易操作")
                content = (
                    "历史操作记录："
                    + action
                    + (f"，订单 {data['orderId']}" if data.get("orderId") else "")
                    + "。该记录仅代表当时的操作结果。"
                )
                experience = {
                    "eventId": memory_turn_id(
                        user_id, session_id, request_id, "experience"
                    ),
                    "eventType": "trade_completed",
                    "content": content,
                    "entityType": "order",
                    "entityId": str(data.get("orderId") or ""),
                    "sourceTurnId": turn_id,
                    "occurredAt": now.isoformat(),
                    "expiresAt": (now + timedelta(days=180)).isoformat(),
                }
            self.enqueue(
                user_id,
                session_id,
                turn_id,
                db=db,
                user=message[:4000],
                assistant=result["response"][:4000],
                intent=result["intent"],
                run_id=result["run_id"],
                experience=experience,
                entity_groups=result.get("entity_groups", []),
                session_constraints=result.get("session_constraints", {}),
            )

    def drain(self, limit=25):
        with self.database.connect() as db:
            jobs = db.execute(
                """
                SELECT job_id,user_id,session_id,kind,payload FROM memory_jobs j
                WHERE status='PENDING' AND next_retry<=? AND NOT EXISTS (
                  SELECT 1 FROM memory_jobs old WHERE old.user_id=j.user_id AND old.kind='turn'
                  AND j.kind='turn' AND old.status='PENDING'
                  AND (old.created<j.created OR (old.created=j.created AND old.job_id<j.job_id)))
                ORDER BY created LIMIT ?
            """,
                (self.storage.now(db), limit),
            ).fetchall()
        for job_id, user_id, session_id, kind, raw in jobs:
            try:
                with self.storage.lease("memory-delivery:" + user_id) as owned:
                    with self.database.connect() as db:
                        if (
                            db.execute(
                                "SELECT status FROM memory_jobs WHERE job_id=?",
                                (job_id,),
                            ).fetchone()[0]
                            != "PENDING"
                        ):
                            continue
                    client = AgentMemoryClient(self.settings, user_id=user_id)
                    if kind == "turn":
                        payload = json.loads(raw)
                        experience = payload.pop("experience", None)
                        client.append(session_id, "", turn_id=job_id, **payload)
                        # Refresh after async /runs too, not only the synchronous
                        # chat path; versioned Redis writes reject older snapshots.
                        memory = SessionMemoryService(self.settings, java_client=client)
                        memory._cache_set(client.load(session_id, ""), user_id)
                        LongTermMemoryService(
                            self.settings, java_client=client
                        ).remember_explicit(
                            payload["user"], user_id, "internal", job_id
                        )
                        if experience:
                            client.save_event("", experience)
                    else:
                        memory = SessionMemoryService(self.settings, java_client=client)
                        context = client.load_pending(session_id)
                        previous_seq = context.summary_through_seq
                        if memory.summary_service.should_summarize(context):
                            updated = memory._maybe_summarize(context, "")
                            if updated.summary_through_seq == previous_seq:
                                raise RuntimeError("summary deferred")
                            memory._cache_set(client.load(session_id, ""), user_id)
                    owned()
                    with self.database.connect() as db:
                        db.execute(
                            "UPDATE memory_jobs SET status='DONE',payload='{}' WHERE job_id=?",
                            (job_id,),
                        )
                        if kind == "summary" and len(context.turns) >= 50:
                            db.execute(
                                "UPDATE memory_jobs SET status='PENDING',next_retry=0 WHERE job_id=?",
                                (job_id,),
                            )
                        if kind == "turn" and self.settings.session_summary_enabled:
                            summary_id = "summary:" + memory_turn_id(
                                user_id, session_id, "summary"
                            )
                            db.execute(
                                """INSERT INTO memory_jobs(job_id,user_id,session_id,kind,payload,created)
                                VALUES(?,?,?,'summary','{}',?) ON CONFLICT(job_id) DO UPDATE SET
                                status='PENDING',next_retry=0,created=excluded.created""",
                                (summary_id, user_id, session_id, self.storage.now(db)),
                            )
                    MEMORY_OPERATIONS.labels(kind, "delivered").inc()
            except Exception as exc:  # noqa: BLE001 - delivery must survive dependency outages
                # 409 is normal lease contention; dependency outages retain the original job.
                with self.database.connect() as db:
                    attempts = db.execute(
                        "SELECT attempts FROM memory_jobs WHERE job_id=?", (job_id,)
                    ).fetchone()[0]
                    permanent = getattr(
                        exc, "response", None
                    ) is not None and exc.response.status_code in {400, 422}
                    db.execute(
                        "UPDATE memory_jobs SET attempts=attempts+1,next_retry=?,status=? WHERE job_id=? AND status='PENDING'",
                        (
                        self.storage.now(db) + min(300, 2 ** min(attempts + 1, 8)),
                            "FAILED" if permanent else "PENDING",
                            job_id,
                        ),
                    )
                MEMORY_OPERATIONS.labels(kind, "failed" if permanent else "retry").inc()
                logger.warning(
                    "memory_delivery_deferred kind=%s error=%s",
                    kind,
                    type(exc).__name__,
                )

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.stopping.clear()

        def run():
            while not self.stopping.is_set():
                try:
                    self.drain()
                except Exception as exc:  # noqa: BLE001 - delivery must survive dependency outages
                    logger.warning(
                        "memory_delivery_unavailable error=%s", type(exc).__name__
                    )
                self.stopping.wait(2)

        self.thread = threading.Thread(target=run, name="memory-delivery", daemon=True)
        self.thread.start()

    def close(self):
        self.stopping.set()
        if self.thread:
            self.thread.join(timeout=35)
        self.database.close()


@lru_cache(maxsize=1)
def get_memory_delivery():
    return MemoryDelivery(get_settings())

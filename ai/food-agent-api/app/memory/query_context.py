from __future__ import annotations

import json
from dataclasses import dataclass

from app.memory.session_memory import SessionTurn, should_use_session_history


@dataclass(frozen=True)
class QueryContext:
    retrieval_query: str
    history_prompt: str = ""
    resolution_text: str = ""

    @property
    def uses_history(self) -> bool:
        return bool(self.history_prompt)


def build_query_context(
    message: str,
    history: list[SessionTurn] | None,
) -> QueryContext:
    if not history or not should_use_session_history(message):
        return QueryContext(retrieval_query=message)

    turns = [turn for turn in history if turn.intent != "summary"][-2:]
    if not turns:
        return QueryContext(retrieval_query=message)

    items = [
        {
            "user": turn.user[:800],
            "assistant": turn.assistant[:1200],
            "intent": turn.intent,
        }
        for turn in turns
    ]
    prior_queries = " ".join(turn.user for turn in turns)[-1200:]
    resolution_text = "\n".join(
        text
        for turn in turns
        for text in (turn.user, turn.assistant)
        if text
    )[-3000:]
    return QueryContext(
        retrieval_query=f"{prior_queries} {message}".strip(),
        history_prompt=json.dumps(items, ensure_ascii=False, separators=(",", ":")),
        resolution_text=resolution_text,
    )

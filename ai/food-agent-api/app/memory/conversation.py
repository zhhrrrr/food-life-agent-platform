"""One bounded context policy for planning and execution, with deterministic references."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from app.memory.entities import (
    LABELS,
    EntityGroup,
    explicit_ids,
    legacy_entity_groups,
    ordinal_index,
)
from app.memory.session_memory import SessionTurn

DEFAULT_TURNS = 2
MAX_TURNS = 8
REFERENCE = re.compile(
    r"这家|那家|这个|那个|这款|那款|这笔|那笔|它|前者|后者|上述|上一个|最后一[个家]|第\s*[一二两三四五六七八九十\d]+\s*[个家款项笔]"
)
RECALL = re.compile(r"之前|刚才|上次|最开始|最初|前面|原来|还记得|我说过")
CONTINUATION = re.compile(
    r"还有|其他|换一家|换一个|换一款|一样|继续|再推荐|再来|更便宜|便宜一点|改成|调整为|预算改|不限预算|预算不限|不限制预算|取消预算|不考虑预算|不要辣|不吃|过敏|忌口|then|previous",
    re.IGNORECASE,
)
RESET = re.compile(r"换个话题|换一个话题|重新开始|不沿用|清空条件|重新推荐")
CONSTRAINTS = {
    "budget": re.compile(r"预算|人均|每人|\d+\s*元(?:以内|以下|以上)|不超过"),
    "dietary": re.compile(r"忌口|过敏|不吃|不要辣|不辣|素食|清真"),
}


@dataclass(frozen=True)
class ConversationSelection:
    history: list[SessionTurn]
    resolved: dict[str, str] = field(default_factory=dict)
    source_turn_id: str = ""
    clarification: str = ""
    follow_up: bool = False
    expansion_reason: str = "recent"

    def resolve_message(self, message: str) -> str:
        extra = [
            f"{LABELS[kind]}ID：{identifier}"
            for kind, identifier in self.resolved.items()
            if kind not in explicit_ids(message)
        ]
        return message + ("\n指代对象：" + "，".join(extra) if extra else "")


def is_follow_up(message: str) -> bool:
    return not RESET.search(message) and bool(
        REFERENCE.search(message)
        or RECALL.search(message)
        or CONTINUATION.search(message)
        or re.match(r"\s*(?:人均|预算|每人|总预算|不辣|微辣)", message)
        or message.strip().endswith(("呢", "呢？", "呢?"))
    )


def _target_type(message: str) -> str:
    if re.search(r"队伍|团号|team", message, re.IGNORECASE):
        return "team"
    if re.search(r"订单|退款|取消|付款|支付", message) and not re.search(
        r"支付规则|退款规则|退款条件", message
    ):
        return "order"
    if re.search(r"这家|那家|第.+家|商家|门店|店铺", message):
        return "shop"
    if re.search(
        r"套餐|这款|那款|库存|多少钱|价格|下单|购买|买它|买这个|买那个", message
    ):
        return "package"
    return ""


def _turn_groups(turn: SessionTurn) -> list[EntityGroup]:
    # Empty groups on new turns are authoritative: do not parse generated text as IDs.
    return (
        turn.entity_groups
        if turn.entities_recorded or turn.entity_groups
        else legacy_entity_groups(turn.assistant)
    )


def select_conversation(
    message: str, history: list[SessionTurn] | None
) -> ConversationSelection:
    history = [
        t if isinstance(t, SessionTurn) else SessionTurn.model_validate(t)
        for t in history or []
    ]
    summaries = [t for t in history if t.intent == "summary"][-1:]
    turns = [t for t in history if t.intent != "summary"][-MAX_TURNS:]
    count = min(DEFAULT_TURNS, len(turns))
    if turns and all(t.context_selected for t in turns):
        count = len(turns)
    resolved = explicit_ids(message)
    follow_up = is_follow_up(message)
    clarification, source, reason = "", "", "recent"
    target = _target_type(message)
    policy_question = not REFERENCE.search(message) and not RECALL.search(message) and re.search(
        r"规则|怎么办|如何|怎么|可以.*吗|能否|能不能", message
    )
    referring = bool(
        REFERENCE.search(message) or (follow_up and target and not policy_question)
    ) and not RESET.search(message)
    if (
        len(
            set(re.findall(r"第\s*[一二两三四五六七八九十\d]+\s*[个家款项笔]", message))
        )
        > 1
        and not resolved
    ):
        return ConversationSelection(
            [*summaries, *turns[-count:]],
            clarification="本次涉及多个对象，请提供要比较的候选ID；交易操作请逐个指定对象。",
            follow_up=True,
        )
    if referring and not (target in resolved or (not target and resolved)):
        search = list(reversed(list(enumerate(turns))))
        if re.search(r"最开始|最初", message):
            search.reverse()
        found = False
        for index, turn in search:
            groups = [
                g
                for g in _turn_groups(turn)
                if g.candidates and (not target or g.entity_type == target)
            ]
            if not groups:
                if RESET.search(turn.user):
                    break
                continue
            found = True
            count = max(count, len(turns) - index)
            source = turn.turn_id or f"seq:{turn.sequence_no}"
            reason = "reference" if count > DEFAULT_TURNS else reason
            ordinal = ordinal_index(message)
            named = {
                (g.entity_type, c.id)
                for g in groups
                for c in g.candidates
                if len(c.label) >= 2 and c.label in message
            }
            # Never merge lists from different tasks, or silently skip an out-of-range latest list.
            if len(named) == 1:
                kind, identifier = named.pop()
                resolved[kind] = identifier
            elif len(groups) != 1:
                clarification = (
                    "这次展示了多组候选，请说明要选哪组，或直接提供商家、套餐或订单ID。"
                )
            else:
                group = groups[0]
                candidates = group.candidates
                named = [
                    c for c in candidates if len(c.label) >= 2 and c.label in message
                ]
                if len(named) == 1:
                    resolved[group.entity_type] = named[0].id
                elif "后者" in message and len(candidates) != 2:
                    clarification = "当前候选数量不是两个，请明确候选序号或ID。"
                elif (
                    ordinal is not None
                    and group.ordinal_safe
                    and -len(candidates) <= ordinal < len(candidates)
                ):
                    resolved[group.entity_type] = candidates[ordinal].id
                elif ordinal is None and len(candidates) == 1:
                    resolved[group.entity_type] = candidates[0].id
                else:
                    choices = "、".join(
                        f"{i}. {c.label or LABELS[group.entity_type]}（ID：{c.id}）"
                        for i, c in enumerate(candidates, 1)
                    )
                    clarification = (
                        "候选展示顺序未确认，请提供明确ID："
                        if not group.ordinal_safe
                        else "请明确要选哪个："
                    ) + choices
            break
        if not found:
            count = len(turns)
            reason = "reference_not_found"
            clarification = (
                "最近8轮中无法确定你指的是哪个对象，请提供商家、套餐或订单名称/ID。"
            )
    elif RECALL.search(message) and not RESET.search(message):
        # Extend only to the nearest turn containing the explicitly requested field/domain.
        fields = [
            pattern for pattern in CONSTRAINTS.values() if pattern.search(message)
        ]
        if not fields:
            fields = [
                re.compile(word)
                for word in ("推荐", "套餐", "订单", "规则")
                if word in message
            ]
        matches = [
            (i, t)
            for i, t in enumerate(turns)
            if any(p.search(t.user + "\n" + t.assistant) for p in fields)
        ]
        if matches:
            index, _ = matches[0] if re.search(r"最开始|最初", message) else matches[-1]
            count = max(count, len(turns) - index)
            reason = "explicit_recall"
        elif fields:
            count = len(turns)
            reason = "recall_not_found"
    if follow_up:
        # Missing active conditions: search backward to their latest user statement.
        for key, pattern in CONSTRAINTS.items():
            saved_key = "dietary_requirements" if key == "dietary" else key
            if pattern.search(message) or any(
                pattern.search(t.user) or saved_key in t.session_constraints
                for t in turns[-count:]
                if count
            ):
                continue
            for index in range(len(turns) - count - 1, -1, -1):
                if RESET.search(turns[index].user):
                    break
                if pattern.search(turns[index].user):
                    count = max(count, len(turns) - index)
                    reason = "missing_constraint"
                    break
    selected = (
        [t.model_copy(update={"context_selected": True}) for t in turns[-count:]]
        if count
        else []
    )
    return ConversationSelection(
        [*summaries, *selected], resolved, source, clarification, follow_up, reason
    )


def reference_intent(message: str, history: list[SessionTurn]) -> str:
    resolved = select_conversation(message, history).resolved
    for turn in reversed(history):
        if any(
            candidate.id == resolved.get(group.entity_type)
            for group in _turn_groups(turn)
            for candidate in group.candidates
        ):
            return turn.intent
    return history[-1].intent if history else ""

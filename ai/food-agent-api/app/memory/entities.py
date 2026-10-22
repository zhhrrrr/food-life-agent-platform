"""References to displayed business objects; never a source of live business facts."""

from __future__ import annotations

import re
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

EntityType = Literal["shop", "package", "order", "team"]
LABELS = {"shop": "商家", "package": "套餐", "order": "订单", "team": "拼团队伍"}
ID_PATTERNS = {
    "shop": re.compile(
        r"(?:商家|门店|店铺|shop)\s*(?:ID|编号|号)?\s*[:：#]?\s*(\d{1,19})(?!\d)",
        re.IGNORECASE,
    ),
    "package": re.compile(
        r"(?:套餐|商品|package)\s*(?:ID|编号|号)?\s*[:：#]?\s*(\d{1,19})(?!\d)",
        re.IGNORECASE,
    ),
    "order": re.compile(
        r"(?:订单|order)\s*(?:ID|编号|号|no)?\s*[:：#]?\s*(\d{1,19})(?!\d)",
        re.IGNORECASE,
    ),
    "team": re.compile(
        r"(?:拼团(?:队伍)?|team)\s*(?:ID|编号|号)\s*[:：#]?\s*([A-Za-z0-9_-]{1,64})",
        re.IGNORECASE,
    ),
}


class EntityCandidate(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    label: str = Field(default="", max_length=160)


class EntityGroup(BaseModel):
    entity_type: EntityType
    source: str = Field(default="", max_length=128)
    candidates: list[EntityCandidate] = Field(default_factory=list, max_length=20)
    ordinal_safe: bool = True

    @model_validator(mode="after")
    def validate_candidates(self):
        pattern = (
            r"[A-Za-z0-9_-]{1,64}" if self.entity_type == "team" else r"[1-9]\d{0,18}"
        )
        ids = [candidate.id for candidate in self.candidates]
        if len(ids) != len(set(ids)) or any(
            not re.fullmatch(pattern, identifier) for identifier in ids
        ):
            raise ValueError("invalid or duplicate entity IDs")
        return self


class BudgetSnapshot(BaseModel):
    min_yuan: int | None = Field(default=None, ge=0, le=1_000_000)
    max_yuan: int | None = Field(default=None, ge=0, le=1_000_000)
    budget_type: Literal["per_person", "total", "package", "unspecified"] = (
        "unspecified"
    )

    @model_validator(mode="after")
    def validate_range(self):
        if (
            self.min_yuan is not None
            and self.max_yuan is not None
            and self.min_yuan > self.max_yuan
        ):
            raise ValueError("invalid budget range")
        return self


ConstraintTerms = list[Annotated[str, Field(min_length=1, max_length=160)]]


class SessionConstraints(BaseModel):
    budget: BudgetSnapshot | None = None
    dietary_requirements: ConstraintTerms = Field(default_factory=list, max_length=20)
    cuisines: ConstraintTerms = Field(default_factory=list, max_length=20)
    areas: ConstraintTerms = Field(default_factory=list, max_length=20)
    scenes: ConstraintTerms = Field(default_factory=list, max_length=20)
    party_size: int | None = Field(default=None, ge=1, le=100)


def explicit_ids(message: str) -> dict[str, str]:
    # Last explicit value wins, including a user's correction after clarification.
    return {
        kind: matches[-1].group(1)
        for kind, pattern in ID_PATTERNS.items()
        if (matches := list(pattern.finditer(message)))
    }


def ordinal_index(message: str) -> int | None:
    match = re.search(
        r"第\s*([一二两三四五六七八九十]+|\d+)\s*(?:个|家|款|项|笔)", message
    )
    if match:
        numbers = {
            "一": 1,
            "二": 2,
            "两": 2,
            "三": 3,
            "四": 4,
            "五": 5,
            "六": 6,
            "七": 7,
            "八": 8,
            "九": 9,
            "十": 10,
        }
        text = match[1]
        number = numbers.get(text, 0)
        if text.isdigit():
            number = int(text) if len(text) <= 3 else 1000
        elif "十" in text and text.count("十") == 1:
            tens, units = text.split("十")
            number = numbers.get(tens, 1 if not tens else 0) * 10 + numbers.get(
                units, 0
            )
        return number - 1 if number > 0 else 99
    if "最后一个" in message or "最后一家" in message:
        return -1
    if "前者" in message:
        return 0
    if "后者" in message:
        return 1
    return None


def displayed_entity_groups(payload: dict, source: str = "") -> list[EntityGroup]:
    """Bind actual answer mentions to IDs from structured evidence, in answer order.

    Unmentioned retrieval hits are excluded. No ID is invented from answer text.
    Each task/list remains separate, including when the composer combines answers.
    """
    if "entity_groups" in payload:
        return [EntityGroup.model_validate(group) for group in payload["entity_groups"]]
    answer = str(payload.get("response") or "")
    known: dict[str, dict[str, str]] = {}
    for item in [
        *(payload.get("context") or []),
        *(payload.get("references") or []),
        *(payload.get("facts") or []),
    ]:
        if not isinstance(item, dict):
            continue
        metadata = item.get("metadata") or item.get("data") or {}
        if not isinstance(metadata, dict):
            continue
        for kind in ("shop", "package", "order", "team"):
            identifier = metadata.get(kind + "Id")
            document_id = str(item.get("id") or "")
            # A package's shopId is a relationship, not another displayed list.
            is_document = document_id.startswith((kind + "-", kind + ":"))
            if not is_document and not document_id and identifier is not None:
                is_document = True
            if not is_document:
                continue
            if identifier is None:
                identifier = document_id[len(kind) + 1 :].split(":", 1)[0]
            if not re.fullmatch(
                r"[A-Za-z0-9_-]{1,64}" if kind == "team" else r"[1-9]\d{0,18}",
                str(identifier),
            ):
                continue
            label = str(metadata.get(kind + "Name") or item.get("title") or "")[:160]
            known.setdefault(kind, {})[str(identifier)] = label
    trade = payload.get("trade") or {}
    data = (trade.get("result") or {}).get("data") or {}
    if isinstance(data, dict):
        for kind in ("package", "order"):
            identifier = data.get(kind + "Id")
            if identifier is not None and re.fullmatch(
                r"[1-9]\d{0,18}", str(identifier)
            ):
                known.setdefault(kind, {})[str(identifier)] = str(
                    data.get(kind + "Name") or ""
                )[:160]
    groups = []
    list_starts = [
        m.start()
        for m in re.finditer(
            r"(?m)^\s*(?:[-*]\s+|\d+[.、）)]\s*|[一二三四五六七八九十]+[、.）)]\s*)|第[一二三四五六七八九十\d]+[个家款](?:是|为|[:：])?",
            answer,
        )
    ]

    def in_list(position: int) -> bool:
        return any(
            start <= position and "\n" not in answer[start:position]
            for start in list_starts
        )

    repeated_numbering = len(re.findall(r"(?m)^\s*1[.、）)]", answer)) > 1
    for kind, candidates in known.items():
        positions = []
        for identifier, label in candidates.items():
            matches = [
                m.start()
                for m in ID_PATTERNS[kind].finditer(answer)
                if m[1] == identifier
            ]
            # Names must uniquely identify one evidence object.
            if (
                len(label) >= 2
                and list(candidates.values()).count(label) == 1
                and label in answer
            ):
                matches.extend(m.start() for m in re.finditer(re.escape(label), answer))
            if matches:
                listed_matches = [p for p in matches if in_list(p)]
                positions.append((min(listed_matches or matches), identifier, label))
        positions.sort()
        # If there is a real list, ignore mentions in the introductory prose.
        listed = [p for p in positions if in_list(p[0])]
        if listed:
            positions = listed
        if positions:
            groups.append(
                EntityGroup(
                    entity_type=kind,
                    source=source,
                    candidates=[
                        EntityCandidate(id=i, label=label)
                        for _, i, label in positions[:20]
                    ],
                    ordinal_safe=(len(positions) == 1 or len(listed) == len(positions))
                    and len(positions) <= 20
                    and not repeated_numbering
                    and len({p for p, _, _ in positions}) == len(positions),
                )
            )
    return groups


def artifact_entity_groups(artifacts: dict) -> list[EntityGroup]:
    groups = []
    for source, artifact in artifacts.items():
        if not isinstance(artifact, dict):
            continue
        if artifact.get("entity_groups"):
            groups.extend(
                EntityGroup.model_validate(g) for g in artifact["entity_groups"]
            )
            continue
        for kind in ("shop", "package", "order"):
            ids = list(
                dict.fromkeys(
                    str(i)
                    for i in artifact.get(kind + "_ids", [])
                    if re.fullmatch(r"[1-9]\d{0,18}", str(i))
                )
            )
            if ids:
                groups.append(
                    EntityGroup(
                        entity_type=kind,
                        source=source,
                        candidates=[EntityCandidate(id=i) for i in ids[:20]],
                        ordinal_safe=len(ids) == 1,
                    )
                )
    return groups


def legacy_entity_groups(answer: str) -> list[EntityGroup]:
    """Conservative compatibility for old turns: one assistant answer, explicit IDs only."""
    groups = []
    for kind, pattern in ID_PATTERNS.items():
        valid = r"[A-Za-z0-9_-]{1,64}" if kind == "team" else r"[1-9]\d{0,18}"
        ids = list(
            dict.fromkeys(
                m[1] for m in pattern.finditer(answer) if re.fullmatch(valid, m[1])
            )
        )
        if ids:
            groups.append(
                EntityGroup(
                    entity_type=kind,
                    source="legacy",
                    candidates=[EntityCandidate(id=i) for i in ids[:20]],
                    ordinal_safe=len(ids) <= 20,
                )
            )
    return groups

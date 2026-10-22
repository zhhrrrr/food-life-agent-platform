"""Small, conservative write policy. Temporary dining conditions stay in the session."""

import logging
import re
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator


class PreferenceUpdate(BaseModel):
    key: Literal["favorite", "avoid", "allergy", "budget"]
    operation: Literal["add", "replace", "remove", "delete"] = "replace"
    values: list[Annotated[str, Field(min_length=1, max_length=40)]] = Field(
        default_factory=list, max_length=20
    )
    max_yuan: int | None = Field(default=None, ge=0, le=100000)
    budget_type: Literal["per_person", "total", "package", "unspecified"] = (
        "unspecified"
    )
    evidence: str = ""

    @model_validator(mode="after")
    def validate_operation(self):
        if self.operation != "delete":
            if self.key == "budget" and (
                self.max_yuan is None or self.operation != "replace"
            ):
                raise ValueError("budget requires an amount and replacement semantics")
            if self.key != "budget" and not self.values:
                raise ValueError("preference values required")
        return self


class PreferenceExtraction(BaseModel):
    updates: list[PreferenceUpdate] = Field(default_factory=list, max_length=8)


def preference_updates(message: str, *, settings=None) -> list[PreferenceUpdate]:
    updates = []
    labels = {
        "喜好": "favorite",
        "偏好": "favorite",
        "忌口": "avoid",
        "过敏": "allergy",
        "预算": "budget",
    }
    for clause in re.split(r"[，。；！？\n]", message):
        if not clause or re.search(
            r"今天|这次|本次|今晚|明天|朋友|同事|孩子|家人|他|她|吗|是否", clause
        ):
            continue
        if re.search(r"忘记|删除|清除", clause):
            updates.extend(
                PreferenceUpdate(key=key, operation="delete", evidence=clause)
                for label, key in labels.items()
                if label in clause
            )
            continue
        allergy = re.search(r"我(?:不再)?对(.{1,30}?)过敏", clause)
        if allergy:
            updates.append(
                PreferenceUpdate(
                    key="allergy",
                    operation="remove" if "不再" in allergy[0] else "add",
                    values=_values(allergy[1]),
                    evidence=clause,
                )
            )
        for key, pattern in (
            ("favorite", r"我(?:喜欢|爱吃|偏好)(.{1,40})"),
            ("avoid", r"我(?:不吃|忌口)(.{1,40})"),
        ):
            match = re.search(pattern, clause)
            if match:
                values = _values(re.split(r"但是|但|不过", match[1])[0])
                updates.append(
                    PreferenceUpdate(
                        key=key,
                        operation="replace" if key == "favorite" else "add",
                        values=values,
                        evidence=clause,
                    )
                )
        budget = re.search(
            r"(?:预算|人均)(?:是|在|大约|不超过)?\s*(\d{1,5})\s*元?", clause
        )
        if budget and re.search(r"记住|平时|通常|长期", clause):
            kind = (
                "per_person"
                if "人均" in clause
                else "total"
                if "总" in clause
                else "package"
                if "套餐" in clause
                else "unspecified"
            )
            updates.append(
                PreferenceUpdate(
                    key="budget",
                    max_yuan=int(budget[1]),
                    budget_type=kind,
                    evidence=clause,
                )
            )
    # LLM is only an optional extractor for explicit, otherwise-unrecognized memory requests.
    if (
        not updates
        and settings
        and settings.llm_router_enabled
        and "记住" in message
        and not re.search(r"今天|这次|本次|朋友|同事|孩子|他|她", message)
    ):
        from app.llm.structured_output import invoke_structured_output

        try:
            result = invoke_structured_output(
                settings=settings,
                schema=PreferenceExtraction,
                messages=[
                    (
                        "system",
                        (
                            "提取用户本人明确要求长期记住的饮食偏好。仅支持favorite/avoid/allergy/budget。"
                            "evidence必须逐字引用原文，values必须出现在原文；不推断、不记录临时条件或他人信息。预算注明人均/总额/套餐。"
                        ),
                    ),
                    ("human", message),
                ],
            )
            updates = [
                u
                for u in result.updates
                if u.evidence
                and u.evidence in message
                and all(v and v in u.evidence for v in u.values)
                and (u.max_yuan is None or str(u.max_yuan) in u.evidence)
            ]
        except Exception as exc:  # noqa: BLE001 - optional extraction must fail closed
            logging.getLogger(__name__).warning(
                "preference_extraction_failed error=%s", type(exc).__name__
            )
    combined = {}
    for update in updates:
        previous = combined.get(update.key)
        if previous and previous.operation == update.operation == "add":
            update = PreferenceUpdate(
                **{
                    **update.model_dump(),
                    "values": list(dict.fromkeys([*previous.values, *update.values])),
                }
            )
        combined[update.key] = update
    return list(combined.values())


def _values(text: str) -> list[str]:
    return list(
        dict.fromkeys(
            v.strip() for v in re.split(r"和|、|以及|还有", text) if v.strip()
        )
    )[:20]


def effective_profile(profile: dict, message: str) -> dict:
    """Apply this request's explicit corrections immediately, before asynchronous persistence."""
    result = dict(profile)
    for update in preference_updates(message):
        if update.operation == "delete":
            result.pop(update.key, None)
        elif update.key == "budget":
            result["budget"] = {
                "max_yuan": update.max_yuan,
                "budget_type": update.budget_type,
            }
        else:
            values = (
                []
                if update.operation == "replace"
                else list(result.get(update.key, []))
            )
            result[update.key] = (
                [v for v in values if v not in update.values]
                if update.operation == "remove"
                else list(dict.fromkeys([*values, *update.values]))
            )
    return result

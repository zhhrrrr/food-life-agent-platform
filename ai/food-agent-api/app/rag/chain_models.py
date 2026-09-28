import logging
from collections.abc import Sequence
from typing import Any, TypeVar

from pydantic import BaseModel, Field, field_validator

from app.core.config import Settings
from app.llm.structured_output import invoke_structured_output_with_fallback

Capability = TypeVar("Capability", bound=str)


class QueryRewriteBase(BaseModel):
    search_query: str = Field(min_length=1)
    reason: str = Field(min_length=1)

    @field_validator("search_query", "reason")
    @classmethod
    def reject_blank_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("value must not be blank")
        return normalized

    @field_validator("capabilities", check_fields=False)
    @classmethod
    def deduplicate_capabilities(cls, values: list[str]) -> list[str]:
        return list(dict.fromkeys(values))


class AgentAnswer(BaseModel):
    answer: str = Field(min_length=1)
    used_reference_ids: list[str] = Field(default_factory=list)

    @field_validator("answer")
    @classmethod
    def reject_blank_answer(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("answer must not be blank")
        return normalized

    @field_validator("used_reference_ids")
    @classmethod
    def deduplicate_reference_ids(cls, values: list[str]) -> list[str]:
        return list(dict.fromkeys(value for value in values if value))


class AgentChainResult(BaseModel):
    response: str
    rag_mode: str
    query: str
    rewritten_query: str
    capabilities: list[str] = Field(default_factory=list)
    context: list[dict[str, object]] = Field(default_factory=list)
    references: list[dict[str, object]] = Field(default_factory=list)


def merge_capabilities(
    primary: list[Capability],
    required: list[Capability],
) -> list[Capability]:
    return list(dict.fromkeys([*primary, *required]))


def select_references(
    references: list[dict[str, object]],
    used_reference_ids: list[str],
) -> list[dict[str, object]]:
    if not used_reference_ids:
        return []
    used = set(used_reference_ids)
    return [reference for reference in references if str(reference.get("id")) in used]


def validate_answer_references(
    answer: AgentAnswer,
    available_reference_ids: list[str],
    fallback: AgentAnswer,
) -> AgentAnswer:
    available = set(available_reference_ids)
    used = set(answer.used_reference_ids)
    if not used or not used.issubset(available):
        return fallback
    return answer


def invoke_grounded_answer(
    *,
    settings: Settings,
    messages: Sequence[tuple[str, str]],
    fallback_answer: str,
    reference_ids: list[str],
    operation: str,
    logger: logging.Logger,
    chat_model: Any | None = None,
) -> AgentAnswer:
    fallback = AgentAnswer(
        answer=fallback_answer,
        used_reference_ids=reference_ids,
    )
    answer = invoke_structured_output_with_fallback(
        settings=settings,
        schema=AgentAnswer,
        messages=messages,
        fallback=fallback,
        operation=operation,
        logger=logger,
        chat_model=chat_model,
    )
    return validate_answer_references(
        answer,
        available_reference_ids=reference_ids,
        fallback=fallback,
    )

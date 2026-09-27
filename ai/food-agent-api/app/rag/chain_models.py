from typing import TypeVar

from pydantic import BaseModel, Field, field_validator

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
        return references
    used = set(used_reference_ids)
    selected = [reference for reference in references if str(reference.get("id")) in used]
    return selected or references

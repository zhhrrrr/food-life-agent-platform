from typing import Any

from pydantic import BaseModel, Field


class SearchDocument(BaseModel):
    id: str
    title: str
    content: str
    source: str
    category: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    embedding: list[float] | None = Field(default=None, exclude=True)


class SearchHit(BaseModel):
    id: str
    title: str
    content: str
    source: str
    score: float = 0.0
    retrieval_scores: dict[str, float] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)


class SearchFilters(BaseModel):
    max_price_cents: int | None = Field(default=None, ge=0)
    active_only: bool = True


class Reference(BaseModel):
    id: str
    title: str
    source: str
    score: float = 0.0

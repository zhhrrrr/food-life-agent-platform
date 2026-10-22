from typing import Any

from pydantic import BaseModel, Field, PositiveInt


class SearchDocument(BaseModel):
    id: str
    title: str
    content: str
    source: str
    category: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class SearchHit(BaseModel):
    id: str
    title: str
    content: str
    source: str
    score: float = 0.0
    retrieval_scores: dict[str, float] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)


class SearchFilters(BaseModel):
    shop_ids: list[PositiveInt] = Field(default_factory=list, max_length=1000)
    # Generic bounds are retained for callers that do not know the price semantics.
    min_price_cents: int | None = Field(default=None, ge=0)
    max_price_cents: int | None = Field(default=None, ge=0)
    min_avg_price_cents: int | None = Field(default=None, ge=0)
    max_avg_price_cents: int | None = Field(default=None, ge=0)
    min_item_price_cents: int | None = Field(default=None, ge=0)
    max_item_price_cents: int | None = Field(default=None, ge=0)
    active_only: bool = True


class Reference(BaseModel):
    id: str
    title: str
    source: str
    score: float = 0.0

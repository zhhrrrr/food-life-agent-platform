from typing import Any

from pydantic import BaseModel, Field


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
    metadata: dict[str, Any] = Field(default_factory=dict)


class Reference(BaseModel):
    id: str
    title: str
    source: str
    score: float = 0.0

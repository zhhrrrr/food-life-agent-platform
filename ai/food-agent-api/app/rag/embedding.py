import math
from typing import Any

from app.core.config import Settings, get_settings
from app.llm.embedding_model import build_embedding_model
from app.search.schemas import SearchDocument


class RagEmbeddingService:
    def __init__(
        self,
        settings: Settings | None = None,
        model: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self._model = model

    def embed_query(self, text: str) -> list[float]:
        vector = self._get_model().embed_query(text)
        return self._validate_vector(vector)

    def embed_documents(self, documents: list[SearchDocument]) -> list[list[float]]:
        if not documents:
            return []

        vectors: list[list[float]] = []
        batch_size = self.settings.rag_embedding_batch_size
        texts = [document_embedding_text(document) for document in documents]
        model = self._get_model()
        for offset in range(0, len(texts), batch_size):
            batch = texts[offset : offset + batch_size]
            batch_vectors = model.embed_documents(batch)
            if len(batch_vectors) != len(batch):
                raise ValueError("embedding response count does not match document count")
            vectors.extend(self._validate_vector(vector) for vector in batch_vectors)
        return vectors

    def _get_model(self) -> Any:
        if self._model is None:
            self._model = build_embedding_model(self.settings)
        return self._model

    def _validate_vector(self, vector: Any) -> list[float]:
        if not isinstance(vector, (list, tuple)):
            raise TypeError("embedding vector must be a list")
        values = [float(value) for value in vector]
        if len(values) != self.settings.rag_embedding_dimensions:
            raise ValueError(
                "embedding dimensions do not match RAG_EMBEDDING_DIMENSIONS"
            )
        if not all(math.isfinite(value) for value in values):
            raise ValueError("embedding vector contains a non-finite value")
        if not any(values):
            raise ValueError("embedding vector has zero norm")
        return values


def document_embedding_text(document: SearchDocument) -> str:
    parts = [document.title]
    if document.category:
        parts.append(document.category)
    parts.append(document.content)
    return "\n".join(part.strip() for part in parts if part.strip())[:12000]

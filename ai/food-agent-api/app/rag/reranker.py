import logging
from typing import Any

import httpx
from pydantic import BaseModel, Field

from app.core.config import Settings, get_settings
from app.llm.embedding_model import SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL
from app.search.schemas import SearchHit

logger = logging.getLogger("food_agent.rag.reranker")


class RerankResult(BaseModel):
    hits: list[SearchHit] = Field(default_factory=list)
    applied: bool = False


class RagReranker:
    def __init__(
        self,
        settings: Settings | None = None,
        http_client: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.http_client = http_client

    def rerank(
        self,
        query: str,
        hits: list[SearchHit],
        *,
        size: int,
    ) -> RerankResult:
        if not self.settings.rag_rerank_enabled or not hits:
            return RerankResult(hits=hits[:size])

        api_key = (
            self.settings.rag_rerank_api_key.strip()
            or self.settings.embedding_api_key.strip()
        )
        if not api_key:
            logger.warning("rag_rerank_skipped reason=missing_api_key")
            return RerankResult(hits=hits[:size])

        base_url = (
            self.settings.rag_rerank_base_url.strip()
            or self.settings.embedding_base_url.strip()
            or SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL
        )
        payload = {
            "model": self.settings.rag_rerank_model,
            "query": query,
            "documents": [_rerank_text(hit) for hit in hits],
            "top_n": len(hits),
            "return_documents": False,
        }
        try:
            response = self._post(
                f"{base_url.rstrip('/')}/rerank",
                headers={"Authorization": f"Bearer {api_key}"},
                json=payload,
            )
            response.raise_for_status()
            results = response.json().get("results", [])
            reranked = self._map_results(hits, results)
        except (httpx.HTTPError, ValueError, TypeError, KeyError) as exc:
            logger.warning("rag_rerank_failed error=%s", type(exc).__name__)
            return RerankResult(hits=hits[:size])
        return RerankResult(hits=reranked[:size], applied=True)

    def _post(self, url: str, **kwargs: Any) -> Any:
        if self.http_client is not None:
            return self.http_client.post(url, **kwargs)
        return httpx.post(
            url,
            timeout=self.settings.rag_rerank_timeout_seconds,
            **kwargs,
        )

    @staticmethod
    def _map_results(
        hits: list[SearchHit],
        results: list[dict[str, Any]],
    ) -> list[SearchHit]:
        if not results:
            raise ValueError("rerank response contains no results")
        reranked: list[SearchHit] = []
        used_indices: set[int] = set()
        for result in results:
            index = int(result["index"])
            score = float(result["relevance_score"])
            if index < 0 or index >= len(hits) or index in used_indices:
                raise ValueError("rerank response contains an invalid document index")
            used_indices.add(index)
            hit = hits[index]
            reranked.append(
                hit.model_copy(
                    update={
                        "score": score,
                        "retrieval_scores": {
                            **hit.retrieval_scores,
                            "rerank": score,
                        },
                    }
                )
            )
        reranked.extend(hit for index, hit in enumerate(hits) if index not in used_indices)
        return reranked


def _rerank_text(hit: SearchHit) -> str:
    return f"{hit.title}\n{hit.content}"[:12000]

from typing import Any

from app.core.config import Settings
from app.rag.embedding import RagEmbeddingService
from app.rag.fusion import reciprocal_rank_fusion
from app.rag.reranker import RagReranker, RerankResult
from app.rag.retriever import RagRetriever
from app.search.schemas import SearchDocument, SearchHit


def _hit(document_id: str, score: float) -> SearchHit:
    return SearchHit(
        id=document_id,
        title=document_id,
        content=f"content for {document_id}",
        source="food_shop_index",
        score=score,
    )


def test_embedding_service_batches_and_validates_dimensions() -> None:
    class FakeModel:
        def __init__(self) -> None:
            self.calls: list[list[str]] = []

        def embed_documents(self, texts: list[str]) -> list[list[float]]:
            self.calls.append(texts)
            return [[1.0, 0.0, 0.0] for _ in texts]

    model = FakeModel()
    service = RagEmbeddingService(
        Settings(rag_embedding_dimensions=3, rag_embedding_batch_size=1),
        model=model,
    )
    vectors = service.embed_documents(
        [
            SearchDocument(
                id="shop-1",
                title="Hot Pot",
                content="Dinner",
                source="food_shop_index",
            ),
            SearchDocument(
                id="shop-2",
                title="Cafe",
                content="Coffee",
                source="food_shop_index",
            ),
        ]
    )

    assert vectors == [[1.0, 0.0, 0.0], [1.0, 0.0, 0.0]]
    assert len(model.calls) == 2


def test_rrf_combines_bm25_and_vector_ranks() -> None:
    fused = reciprocal_rank_fusion(
        [
            ("bm25", [_hit("shop-1", 8.0), _hit("shop-2", 4.0)]),
            ("vector", [_hit("shop-2", 1.9), _hit("shop-3", 1.8)]),
        ],
        rank_constant=60,
    )

    assert [hit.id for hit in fused] == ["shop-2", "shop-1", "shop-3"]
    assert fused[0].retrieval_scores["bm25"] == 4.0
    assert fused[0].retrieval_scores["vector"] == 1.9
    assert fused[0].retrieval_scores["rrf"] > fused[1].retrieval_scores["rrf"]


def test_reranker_uses_provider_order_and_score() -> None:
    class FakeResponse:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict[str, Any]:
            return {
                "results": [
                    {"index": 1, "relevance_score": 0.9},
                    {"index": 0, "relevance_score": 0.2},
                ]
            }

    class FakeHttpClient:
        request: dict[str, Any] | None = None

        def post(self, url: str, **kwargs: Any) -> FakeResponse:
            self.request = {"url": url, **kwargs}
            return FakeResponse()

    client = FakeHttpClient()
    reranker = RagReranker(
        Settings(
            rag_rerank_enabled=True,
            rag_rerank_min_score=0.5,
            rag_rerank_api_key="test-key",
            rag_rerank_base_url="https://example.test/v1",
        ),
        http_client=client,
    )
    result = reranker.rerank(
        "hot pot",
        [_hit("shop-1", 0.03), _hit("shop-2", 0.02)],
        size=2,
    )

    assert result.applied is True
    assert [hit.id for hit in result.hits] == ["shop-2"]
    assert result.hits[0].score == 0.9
    assert result.hits[0].retrieval_scores["rerank"] == 0.9
    assert client.request is not None
    assert client.request["json"]["return_documents"] is False


def test_reranker_rejects_all_low_confidence_evidence() -> None:
    class FakeResponse:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict[str, Any]:
            return {
                "results": [
                    {"index": 0, "relevance_score": 0.19},
                    {"index": 1, "relevance_score": 0.05},
                ]
            }

    class FakeHttpClient:
        def post(self, url: str, **kwargs: Any) -> FakeResponse:
            return FakeResponse()

    result = RagReranker(
        Settings(
            rag_rerank_enabled=True,
            rag_rerank_min_score=0.2,
            rag_rerank_api_key="test-key",
        ),
        http_client=FakeHttpClient(),
    ).rerank(
        "unrelated question",
        [_hit("shop-1", 0.03), _hit("shop-2", 0.02)],
        size=2,
    )

    assert result.applied is True
    assert result.hits == []


def test_retriever_runs_hybrid_rrf_and_rerank() -> None:
    class FakeEmbeddingService:
        def embed_query(self, text: str) -> list[float]:
            assert text == "hot pot dinner\nfind dinner"
            return [1.0, 0.0]

    class FakeSearchService:
        def search_many(self, *args: Any, **kwargs: Any) -> list[SearchHit]:
            return [_hit("shop-1", 8.0), _hit("shop-2", 4.0)]

    class FakeVectorService:
        def search_many(self, *args: Any, **kwargs: Any) -> list[SearchHit]:
            assert args[0] == [
                "food_shop_vectors",
                "food_package_vectors",
                "food_review_vectors",
                "food_activity_vectors",
                "food_coupon_vectors",
            ]
            assert args[1] == [1.0, 0.0]
            return [_hit("shop-2", 1.9), _hit("shop-3", 1.8)]

    class FakeReranker:
        def rerank(
            self,
            query: str,
            hits: list[SearchHit],
            *,
            size: int,
        ) -> RerankResult:
            assert query == "find dinner"
            assert hits[0].id == "shop-2"
            return RerankResult(hits=hits[:size], applied=True)

    result = RagRetriever(
        settings=Settings(
            rag_hybrid_enabled=True,
            milvus_enabled=True,
            rag_rerank_enabled=True,
            rag_candidate_size=3,
            rag_embedding_dimensions=2,
        ),
        search_service=FakeSearchService(),  # type: ignore[arg-type]
        embedding_service=FakeEmbeddingService(),  # type: ignore[arg-type]
        vector_service=FakeVectorService(),  # type: ignore[arg-type]
        reranker=FakeReranker(),  # type: ignore[arg-type]
    ).retrieve(
        "recommend",
        "hot pot dinner",
        original_query="find dinner",
        size=3,
    )

    assert result.mode == "hybrid_rrf_rerank"
    assert result.candidate_counts == {"bm25": 2, "vector": 2, "fused": 3}
    assert [hit.id for hit in result.hits] == ["shop-2", "shop-1"]


def test_retriever_skips_rrf_when_only_vector_channel_has_hits() -> None:
    class FakeEmbeddingService:
        def embed_query(self, text: str) -> list[float]:
            return [1.0, 0.0]

    class EmptySearchService:
        def search_many(self, *args: Any, **kwargs: Any) -> list[SearchHit]:
            return []

    class FakeVectorService:
        def search_many(self, *args: Any, **kwargs: Any) -> list[SearchHit]:
            return [_hit("shop-1", 0.88)]

    result = RagRetriever(
        settings=Settings(
            rag_hybrid_enabled=True,
            milvus_enabled=True,
            rag_embedding_dimensions=2,
        ),
        search_service=EmptySearchService(),  # type: ignore[arg-type]
        embedding_service=FakeEmbeddingService(),  # type: ignore[arg-type]
        vector_service=FakeVectorService(),  # type: ignore[arg-type]
    ).retrieve("recommend", "quiet dinner", size=3)

    assert result.mode == "vector"
    assert result.hits[0].score == 0.88
    assert result.candidate_counts == {"bm25": 0, "vector": 1, "fused": 1}

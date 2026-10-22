import pytest

from app.core.config import Settings
from app.rag.reranker import RerankResult
from app.rag.retriever import RagRetriever
from app.recommend.chain import RecommendChain, _fallback_rewrite
from app.search.schemas import SearchDocument, SearchFilters, SearchHit


def test_generic_budget_discovery_keeps_filters_and_answer_question():
    message = "这次改为人均80元以内，推荐适合聚餐的餐厅并说明价格依据。"

    class Search:
        def search_many(self, indices, query, **kwargs):
            assert query == "聚餐 餐厅"
            assert message in kwargs["original_query"]
            assert kwargs["filters"].max_avg_price_cents == 8000
            return [SearchHit(id="shop-1", title="测试餐厅", content="适合朋友聚餐",
                              source="food_shop_index", metadata={"avgPrice": 6800})]

    class Ranker:
        def rerank(self, query, hits, **kwargs):
            assert query == "聚餐 餐厅"
            return RerankResult(hits=hits, applied=True)

    settings = Settings()
    chain = RecommendChain(settings, retriever=RagRetriever(
        settings, search_service=Search(), reranker=Ranker()))
    result = chain.run(message)
    assert result.query == message
    assert result.rewritten_query == "聚餐 餐厅"
    assert result.context[0]["metadata"]["avgPrice"] == "68.00 CNY"
    assert result.session_constraints["budget"]["max_yuan"] == 80


@pytest.mark.parametrize("message,profile,expected", [
    ("今天改吃粤菜，推荐适合聚餐的餐厅，说明为何符合今天的要求。", {}, "粤菜 聚餐 餐厅"),
    ("推荐适合聚餐的餐厅。我对花生过敏，请说明原料和交叉接触是否已核验。",
     {"allergy": ["花生"]}, "聚餐 餐厅"),
    ("推荐适合聚餐的陶陶居餐厅", {}, None),
    ("推荐适合聚餐的雨后小馆", {}, None),
    ("推荐陶陶居和雨后小馆，比较价格", {}, None),
    ("推荐套餐1和套餐2", {}, None),
    ("推荐这家店的套餐", {}, None),
    ("推荐适合聚餐的餐厅，必须有停车位", {}, None),
    ("推荐适合聚餐的‘餐厅’", {}, None),
])
def test_discovery_focus_requires_every_clause_to_be_understood(message, profile, expected):
    from app.recommend.chain import _discovery_query

    rewrite = _fallback_rewrite(message)
    assert _discovery_query(message, rewrite, profile) == expected


def test_per_person_recommendation_requires_linked_shop_price_evidence():
    class Search:
        def search_many(self, *args, **kwargs):
            return [SearchHit(id=f"package-{i}", title="聚餐套餐", content="适合聚餐",
                              source="food_package_index", score=10-i,
                              metadata={"shopId": i, "price": 12000}) for i in (1, 2, 3)]

        def get_document(self, index, document_id):
            assert index == "food_shop_index"
            if document_id == "shop-3":
                return None
            return SearchDocument(id=document_id, title="餐厅", content="餐厅信息", source=index,
                                  metadata={"shopId": int(document_id[5:]),
                                            "avgPrice": 6800 if document_id == "shop-1" else 9800})

    settings = Settings()
    result = RagRetriever(settings, search_service=Search()).retrieve(
        "recommend", "聚餐餐厅", size=5, filters=SearchFilters(max_avg_price_cents=8000))
    assert {h.id for h in result.hits} == {"shop-1", "package-1"}
    assert next(h for h in result.hits if h.id == "shop-1").metadata["avgPrice"] == 6800


def test_support_ranks_canonical_rule_query_and_preserves_original_question(monkeypatch):
    from app.support.chain import SupportChain, SupportQueryRewrite

    message = "没有查任何订单，不能断言退款成功，请解释一般退款规则。"
    canonical = "退款规则 退款成功条件"

    class Search:
        def search_many(self, indices, query, **kwargs):
            assert query == canonical
            assert kwargs["original_query"] == message
            return [SearchHit(id="rule-refund", title="退款规则", content="退款成功以实际查询为准",
                              source="food_rule_index")]

    class Ranker:
        def rerank(self, query, hits, **kwargs):
            assert query == canonical
            return RerankResult(hits=hits, applied=True)

    settings = Settings()
    chain = SupportChain(settings, retriever=RagRetriever(
        settings, search_service=Search(), reranker=Ranker()))
    monkeypatch.setattr(chain, "rewrite_query", lambda *args: SupportQueryRewrite(
        search_query=canonical, capabilities=["refund_rule"], reason="Extracted rule topic"))
    result = chain.run(message)
    assert result.query == message
    assert result.references[0]["id"] == "rule-refund"


def test_per_person_recall_starts_with_priced_shops_before_related_packages():
    cheap = SearchHit(id="shop-1", title="餐厅", content="聚餐", source="food_shop_index",
                      score=.8, metadata={"shopId": 1, "avgPrice": 6800})

    class Search:
        def search_many(self, *args, **kwargs):
            return []

        def get_document(self, index, document_id):
            assert document_id == "shop-1"
            return SearchDocument(**cheap.model_dump(exclude={"score", "retrieval_scores"}))

    class Embedding:
        def embed_query(self, query):
            return [1., 0.]

    class Vectors:
        def search_many(self, collections, vector, **kwargs):
            if collections == ["food_shop_vectors"]:
                assert kwargs["filters"].max_avg_price_cents == 8000
                return [cheap]
            assert kwargs["filters"].shop_ids == [1]
            return [SearchHit(id="package-1", title="聚餐套餐", content="朋友聚餐", score=.9,
                              source="food_package_index", metadata={"shopId": 1})]

    result = RagRetriever(Settings(rag_hybrid_enabled=True, milvus_enabled=True),
                          search_service=Search(), embedding_service=Embedding(),
                          vector_service=Vectors()).retrieve(
        "recommend", "聚餐餐厅", filters=SearchFilters(max_avg_price_cents=8000))
    assert {h.id for h in result.hits} == {"shop-1", "package-1"}


def test_shop_id_constraint_reaches_both_search_backends():
    from app.search.search_service import SearchService
    from app.vector.milvus_service import MilvusVectorService

    filters = SearchFilters(shop_ids=[1, 2])
    assert {"terms": {"metadata.shopId": [1, 2]}} in SearchService._filter_clauses(filters)
    expression = MilvusVectorService._filter_expression(filters)
    parser = pytest.importorskip("milvus_lite.search.filter.parser")
    assert parser.parse_expr(expression) is not None


def test_budget_source_limit_never_orphans_a_candidate_from_its_shop_price():
    class Search:
        def search_many(self, *args, **kwargs):
            return [SearchHit(id=f"package-{i}" if i < 3 else "review-3", title="聚餐候选",
                              content="聚餐", source="food_package_index" if i < 3 else "food_review_index",
                              metadata={"shopId": i}, score=10-i) for i in (1, 2, 3)]

        def get_document(self, index, document_id):
            return SearchDocument(id=document_id, title="餐厅", content="商家信息", source=index,
                                  metadata={"shopId": int(document_id[5:]), "avgPrice": 6800})

    result = RagRetriever(Settings(), search_service=Search()).retrieve(
        "recommend", "聚餐餐厅", size=5, filters=SearchFilters(max_avg_price_cents=8000))
    shop_ids = {h.metadata["shopId"] for h in result.hits if h.id.startswith("shop-")}
    assert all(h.metadata["shopId"] in shop_ids for h in result.hits)


def test_focused_allergy_discovery_excludes_known_ingredients_and_warns_for_unknown():
    from app.rag.retriever import RetrievalResult

    class Retriever:
        def retrieve(self, *args, **kwargs):
            assert kwargs["rerank_query"] == "聚餐 餐厅"
            return RetrievalResult(agent="recommend", query="聚餐 餐厅", hits=[
                SearchHit(id="shop-1", title="含花生候选", content="聚餐",
                          source="food_shop_index", metadata={"ingredients": ["花生"]}),
                SearchHit(id="shop-2", title="原料未核验候选", content="聚餐", source="food_shop_index")])

    result = RecommendChain(Settings(), retriever=Retriever()).run(
        "推荐适合聚餐的餐厅。我对花生过敏，请说明原料和交叉接触是否已核验。")
    assert [item["id"] for item in result.context] == ["shop-2"]
    assert "花生" in result.response and "未完整核验原料与交叉接触" in result.response


def test_fully_parsed_generic_discovery_skips_redundant_model_rewrite(monkeypatch):
    calls = []

    def model_rewrite(**kwargs):
        calls.append(kwargs)
        return kwargs["fallback"]

    monkeypatch.setattr("app.recommend.chain.invoke_structured_output_with_fallback", model_rewrite)
    chain = RecommendChain(Settings())
    result = chain.rewrite_query("今天改吃粤菜，推荐适合聚餐的餐厅，说明为何符合今天的要求。")
    assert result.cuisines == ["粤菜"] and result.scenes == ["聚餐"]
    assert calls == []
    chain.rewrite_query("推荐雨后小馆的套餐")
    assert len(calls) == 1

from scripts.evaluate_rag import _ndcg_at_k, _percentile


def test_ndcg_rewards_relevant_documents_near_the_top() -> None:
    expected = {"doc-a", "doc-b"}

    high = _ndcg_at_k(["doc-a", "doc-b", "doc-c"], expected, top_k=3)
    low = _ndcg_at_k(["doc-c", "doc-a", "doc-b"], expected, top_k=3)

    assert high == 1.0
    assert low < high


def test_ndcg_match_any_treats_duplicate_ids_as_one_relevance_group() -> None:
    expected = {"duplicate-a", "duplicate-b", "duplicate-c"}

    score = _ndcg_at_k(
        ["duplicate-b", "duplicate-c"],
        expected,
        top_k=5,
        match_any=True,
    )

    assert score == 1.0


def test_percentile_uses_nearest_rank() -> None:
    assert _percentile([10.0, 20.0, 30.0, 40.0], 0.95) == 40.0
    assert _percentile([], 0.95) == 0.0

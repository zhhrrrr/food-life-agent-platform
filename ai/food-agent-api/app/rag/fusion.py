from app.search.schemas import SearchHit


def reciprocal_rank_fusion(
    rankings: list[tuple[str, list[SearchHit]]],
    *,
    rank_constant: int = 60,
) -> list[SearchHit]:
    hits_by_id: dict[str, SearchHit] = {}
    fused_scores: dict[str, float] = {}
    channel_scores: dict[str, dict[str, float]] = {}

    for channel, hits in rankings:
        for rank, hit in enumerate(hits, start=1):
            hits_by_id.setdefault(hit.id, hit)
            fused_scores[hit.id] = fused_scores.get(hit.id, 0.0) + 1.0 / (
                rank_constant + rank
            )
            channel_scores.setdefault(hit.id, {})[channel] = hit.score

    return [
        hits_by_id[document_id].model_copy(
            update={
                "score": fused_scores[document_id],
                "retrieval_scores": {
                    **channel_scores[document_id],
                    "rrf": fused_scores[document_id],
                },
            }
        )
        for document_id in sorted(
            hits_by_id,
            key=lambda item: fused_scores[item],
            reverse=True,
        )
    ]

from app.search.schemas import Reference, SearchHit


def build_context_items(hits: list[SearchHit]) -> list[dict[str, object]]:
    return [
        {
            "rank": index,
            "id": hit.id,
            "title": hit.title,
            "source": hit.source,
            "content": hit.content,
            "score": hit.score,
            "metadata": hit.metadata,
        }
        for index, hit in enumerate(hits, start=1)
    ]


def build_context_summary(hits: list[SearchHit]) -> str:
    if not hits:
        return ""

    lines = []
    for index, hit in enumerate(hits, start=1):
        source = f" [{hit.source}]" if hit.source else ""
        lines.append(f"{index}. {hit.title}{source}: {hit.content}")
    return "\n".join(lines)


def build_references(hits: list[SearchHit]) -> list[Reference]:
    return [
        Reference(
            id=hit.id,
            title=hit.title,
            source=hit.source,
            score=hit.score,
        )
        for hit in hits
    ]

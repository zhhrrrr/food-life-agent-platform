from app.search.schemas import Reference, SearchHit


def build_context_summary(hits: list[SearchHit]) -> str:
    if not hits:
        return ""

    lines = []
    for index, hit in enumerate(hits, start=1):
        lines.append(f"{index}. {hit.title}: {hit.content}")
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

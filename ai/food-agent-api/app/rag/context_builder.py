import json
from dataclasses import dataclass
from html import escape
from typing import Any

from app.search.schemas import Reference, SearchHit

_METADATA_FIELDS_BY_ID_PREFIX = {
    "shop-": (
        "shopId",
        "shopName",
        "categoryName",
        "area",
        "address",
        "avgPrice",
        "score",
        "openHours",
    ),
    "package-": (
        "packageId",
        "packageName",
        "shopId",
        "shopName",
        "price",
        "originalPrice",
        "status",
        "useRule",
        "factSource",
        "factStatus",
    ),
    "review-": (
        "reviewId",
        "reviewNo",
        "shopId",
        "shopName",
        "packageId",
        "packageName",
        "score",
        "createTime",
    ),
    "group-buy-activity-": (
        "activityId",
        "activityType",
        "packageId",
        "targetCount",
        "userTakeLimit",
        "activityPrice",
        "validStartTime",
        "validEndTime",
    ),
    "seckill-activity-": (
        "activityId",
        "activityType",
        "packageId",
        "userTakeLimit",
        "activityPrice",
        "validStartTime",
        "validEndTime",
    ),
    "coupon-template-": (
        "couponId",
        "couponName",
        "couponType",
        "thresholdAmount",
        "discountAmount",
        "scopeType",
        "scopeShopId",
        "scopePackageId",
        "userReceiveLimit",
        "validStartTime",
        "validEndTime",
    ),
    "rule-": (
        "doc_type",
        "domain",
        "tags",
        "source_type",
        "language",
        "version",
        "effectiveStartTime",
        "effectiveEndTime",
    ),
}
_MONEY_FIELDS = {
    "avgPrice",
    "price",
    "originalPrice",
    "activityPrice",
    "thresholdAmount",
    "discountAmount",
}


@dataclass(frozen=True)
class RagContext:
    prompt: str
    hits: list[SearchHit]
    items: list[dict[str, object]]
    references: list[Reference]


def build_context(hits: list[SearchHit], max_chars: int = 8000) -> RagContext:
    if not hits:
        return RagContext(prompt="", hits=[], items=[], references=[])

    blocks: list[str] = []
    included_hits: list[SearchHit] = []
    used_chars = 0
    for index, hit in enumerate(hits, start=1):
        remaining = max_chars - used_chars
        if remaining <= 0:
            break
        block = _build_evidence_block(hit, index, remaining)
        if not block:
            break
        if len(block) > remaining:
            break
        blocks.append(block)
        included_hits.append(hit)
        used_chars += len(block) + 1
    return RagContext(
        prompt="\n".join(blocks),
        hits=included_hits,
        items=[
            {
                "rank": index,
                "id": hit.id,
                "title": hit.title,
                "source": hit.source,
                "content": hit.content,
                "score": hit.score,
                "metadata": public_metadata(hit),
            }
            for index, hit in enumerate(included_hits, start=1)
        ],
        references=[
            Reference(
                id=hit.id,
                title=hit.title,
                source=hit.source,
                score=hit.score,
            )
            for hit in included_hits
        ],
    )


def build_template_answer(
    heading: str,
    context: RagContext,
    *,
    max_items: int = 3,
    max_content_chars: int = 240,
) -> str:
    lines = [heading]
    for index, item in enumerate(context.items[:max_items], start=1):
        title = str(item["title"]).strip()
        content = " ".join(str(item["content"]).split())
        if len(content) > max_content_chars:
            content = f"{content[: max_content_chars - 3].rstrip()}..."
        lines.append(f"{index}. {title}: {content}" if content else f"{index}. {title}")
    return "\n".join(lines)


def public_metadata(hit: SearchHit) -> dict[str, Any]:
    fields = next(
        (
            allowed
            for prefix, allowed in _METADATA_FIELDS_BY_ID_PREFIX.items()
            if hit.id.startswith(prefix)
        ),
        (),
    )
    if hit.id.startswith("package-") and hit.metadata.get("factSource") == "business_api":
        fields = (*fields, "stock", "sold", "shopSold")
    return {
        field: _display_value(field, hit.metadata[field])
        for field in fields
        if hit.metadata.get(field) is not None
    }


def _display_value(field: str, value: Any) -> Any:
    if field in _MONEY_FIELDS and isinstance(value, int | float):
        return f"{value / 100:.2f} CNY"
    return value


def _build_evidence_block(hit: SearchHit, rank: int, max_chars: int) -> str:
    title = escape(hit.title, quote=False)
    content = escape(hit.content, quote=False)
    facts = escape(
        json.dumps(public_metadata(hit), ensure_ascii=False, separators=(",", ":")),
        quote=False,
    )
    opening = (
        f'<evidence rank="{rank}" id="{escape(hit.id, quote=True)}" '
        f'source="{escape(hit.source, quote=True)}">\n'
    )

    def render(body: str, metadata: str = facts) -> str:
        return (
            f"{opening}title: {title}\n"
            f"content: {body}\n"
            f"facts: {metadata}\n"
            "</evidence>"
        )

    block = render(content)
    if len(block) <= max_chars:
        return block

    empty_content = render("")
    if len(empty_content) > max_chars:
        empty_content = render("", "{}")
    available_content_chars = max_chars - len(empty_content)
    if available_content_chars <= 1:
        return ""
    return render(
        f"{content[: available_content_chars - 3]}...",
        "{}" if facts not in empty_content else facts,
    )

"""Enrich search candidates with current facts through the shared tool runtime."""

from pydantic import BaseModel, Field, ValidationError

from app.search.schemas import SearchFilters, SearchHit
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient


class PackageFacts(BaseModel):
    packageId: int = Field(gt=0)
    price: int = Field(ge=0)
    originalPrice: int | None = Field(default=None, ge=0)
    stock: int = Field(ge=0)
    sold: int = Field(ge=0)
    shopSold: int = Field(ge=0)
    packageStatus: int


class PackageCandidateEnricher:
    def __init__(self, client: TradeGatewayClient) -> None:
        self.executor = ToolExecutor(build_trade_tool_registry(client), max_calls=1,
                                     settings=getattr(client, "settings", None))

    def enrich(
        self, hits: list[SearchHit], *, filters: SearchFilters, sort_by: str
    ) -> list[SearchHit]:
        package_ids = list(dict.fromkeys(
            int(hit.id.removeprefix("package-"))
            for hit in hits
            if hit.id.startswith("package-") and hit.id.removeprefix("package-").isdigit()
            and int(hit.id.removeprefix("package-")) > 0
        ))
        if not package_ids:
            return hits

        execution = self.executor.execute(
            [ToolCall(name="trade_get_package_snapshots", arguments={"package_ids": package_ids})],
            ToolContext(allowed_tool_names=frozenset({"trade_get_package_snapshots"})),
        )[0]
        result = execution.result
        available = (execution.succeeded and isinstance(result, GatewayQueryResult)
                     and result.has_data and isinstance(result.data, list))
        facts_by_id: dict[int, PackageFacts] = {}
        if available:
            for item in result.data:
                try:
                    facts = PackageFacts.model_validate(item)
                except ValidationError:
                    # An invalid/old API response must never fall back to indexed sales.
                    available = False
                    break
                facts_by_id[facts.packageId] = facts

        enriched: list[SearchHit] = []
        for hit in hits:
            suffix = hit.id.removeprefix("package-")
            if not hit.id.startswith("package-") or not suffix.isdigit():
                enriched.append(hit)
                continue
            metadata = {key: value for key, value in hit.metadata.items()
                        if key not in {"sold", "stock", "shopSold", "factSource", "factStatus"}}
            facts = facts_by_id.get(int(suffix)) if available else None
            if available and facts is None:
                continue  # Deleted candidates are omitted from the batch response.
            if facts is None:
                # Keep descriptive discovery evidence; don't present stale trading facts.
                for key in ("price", "originalPrice", "status"):
                    metadata.pop(key, None)
                metadata["factStatus"] = "unavailable"
            else:
                if facts.packageStatus != 1 or facts.stock <= 0:
                    continue
                lower = filters.min_item_price_cents
                upper = filters.max_item_price_cents
                if lower is None:
                    lower = filters.min_price_cents
                if upper is None:
                    upper = filters.max_price_cents
                if (lower is not None and facts.price < lower) or (upper is not None and facts.price > upper):
                    continue
                metadata.update(facts.model_dump(exclude_none=True, exclude={"packageStatus"}))
                metadata.update(status=facts.packageStatus, factSource="business_api", factStatus="success")
            enriched.append(hit.model_copy(update={"metadata": metadata}))

        if available and sort_by in {"sales_desc", "price_asc"}:
            packages = iter(sorted(
                (hit for hit in enriched if hit.metadata.get("factSource") == "business_api"),
                key=lambda hit: hit.metadata["sold" if sort_by == "sales_desc" else "price"],
                reverse=sort_by == "sales_desc",
            ))
            # Preserve source diversity and sort only the retrieved package candidates.
            enriched = [next(packages) if hit.metadata.get("factSource") == "business_api" else hit
                        for hit in enriched]
        return enriched

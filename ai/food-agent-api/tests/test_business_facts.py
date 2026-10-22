import json
from types import SimpleNamespace

import pytest

from app.core.config import Settings
from app.mcp.server import McpTradeAdapter
from app.rag.context_builder import build_context
from app.recommend.business_facts import PackageCandidateEnricher
from app.recommend.chain import RecommendChain
from app.rag.retriever import RetrievalResult
from app.search.schemas import SearchFilters, SearchHit
from app.sync.es_sync_consumer import EsSyncConsumer
from app.tools.executor import ToolExecutor
from app.tools.models import ToolCall, ToolContext
from app.tools.trade_tools import build_trade_tool_registry
from app.trade.gateway_client import GatewayQueryResult, TradeGatewayClient


def hit(package_id: int, **metadata: object) -> SearchHit:
    return SearchHit(id=f"package-{package_id}", title=f"Package {package_id}",
                     content="双人套餐", source="food_package_index",
                     metadata={"packageId": package_id, "sold": 999999, **metadata})


def facts(package_id: int, **values: object) -> dict[str, object]:
    return {"packageId": package_id, "price": 9800, "originalPrice": 12000,
            "stock": 3, "sold": 12, "shopSold": 50, "packageStatus": 1, **values}


class FakeGateway:
    def __init__(self, data: object, status: str = "success") -> None:
        self.data = data
        self.status = status
        self.calls: list[list[int]] = []

    def get_package_snapshots(self, package_ids: list[int]) -> GatewayQueryResult:
        self.calls.append(package_ids)
        return GatewayQueryResult(capability="package_snapshots", status=self.status, data=self.data)


def test_batches_deduplicates_and_uses_current_sales_for_candidate_sorting() -> None:
    gateway = FakeGateway([facts(1), facts(2, sold=100)])
    hits = PackageCandidateEnricher(gateway).enrich(
        [hit(1), hit(2), hit(1)], filters=SearchFilters(), sort_by="sales_desc")
    assert gateway.calls == [[1, 2]]
    assert [item.id for item in hits] == ["package-2", "package-1", "package-1"]
    context = build_context(hits)
    assert context.items[0]["metadata"]["sold"] == 100
    assert context.items[0]["metadata"]["stock"] == 3
    assert context.items[0]["metadata"]["factSource"] == "business_api"
    assert "999999" not in context.prompt


def test_indexed_sales_are_never_grounding_facts() -> None:
    assert "sold" not in build_context([hit(1)]).items[0]["metadata"]


@pytest.mark.parametrize("data,status", [(None, "unavailable"), ([{"packageId": 1}], "success")])
def test_failure_never_falls_back_to_stale_price_or_sales(data: object, status: str) -> None:
    enriched = PackageCandidateEnricher(FakeGateway(data, status)).enrich(
        [hit(1, price=1, stock=999, factSource="business_api")],
        filters=SearchFilters(), sort_by="sales_desc")
    metadata = build_context(enriched).items[0]["metadata"]
    assert metadata["factStatus"] == "unavailable"
    assert not {"price", "stock", "sold", "factSource"} & metadata.keys()


def test_rechecks_current_budget_stock_and_status_and_omits_deleted_candidates() -> None:
    gateway = FakeGateway([facts(1, price=11000), facts(2, stock=0), facts(3, packageStatus=0), facts(4)])
    enriched = PackageCandidateEnricher(gateway).enrich(
        [hit(item) for item in range(1, 6)],
        filters=SearchFilters(max_item_price_cents=10000), sort_by="relevance")
    assert [item.id for item in enriched] == ["package-4"]


def test_recommend_chain_adds_business_facts_before_building_evidence() -> None:
    class Retriever:
        def retrieve(self, agent: str, query: str, **kwargs: object) -> RetrievalResult:
            return RetrievalResult(agent=agent, query=query, hits=[hit(1)])
    result = RecommendChain(settings=Settings(llm_router_enabled=False),
                            retriever=Retriever(), gateway_client=FakeGateway([facts(1)])).run("推荐双人套餐")
    assert result.context[0]["metadata"]["sold"] == 12
    assert result.context[0]["metadata"]["factSource"] == "business_api"


@pytest.mark.parametrize("ids", [[], [-1], [1] * 51])
def test_batch_tool_rejects_invalid_arguments_before_request(ids: list[int]) -> None:
    gateway = FakeGateway([])
    result = ToolExecutor(build_trade_tool_registry(gateway)).execute(
        [ToolCall(name="trade_get_package_snapshots", arguments={"package_ids": ids})],
        ToolContext(allowed_tool_names=frozenset({"trade_get_package_snapshots"})))[0]
    assert result.status == "invalid_arguments"
    assert gateway.calls == []


def test_mcp_uses_same_batch_tool() -> None:
    gateway = FakeGateway([facts(1)])
    adapter = McpTradeAdapter(Settings(mcp_internal_token="test-token"), gateway)
    result = adapter.execute("trade_get_package_snapshots", {"package_ids": [1]},
                             {"x-mcp-internal-token": "test-token"})
    assert result["data"][0]["sold"] == 12
    assert gateway.calls == [[1]]


def test_gateway_batch_is_one_allowlisted_request(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = []
    def get(url: str, **kwargs: object) -> object:
        calls.append((url, kwargs))
        return SimpleNamespace(status_code=200, json=lambda: {"code": "0000", "data": [facts(1)]})
    monkeypatch.setattr("app.trade.gateway_client.httpx.get", get)
    result = TradeGatewayClient(Settings(java_gateway_base_url="http://test")).get_package_snapshots([1, 1, 2])
    assert result.has_data
    assert len(calls) == 1
    assert calls[0][0] == "http://test/api/package/trade-snapshots"
    assert calls[0][1]["params"] == {"packageIds": "1,2"}


@pytest.mark.parametrize("tag", ["stock.occupied", "stock.released", "stock.sold.confirmed", "stock.rollback", "package.changed"])
def test_old_stock_events_do_not_initialize_or_write_indexes(tag: str, monkeypatch: pytest.MonkeyPatch) -> None:
    def forbidden(**kwargs: object) -> object:
        raise AssertionError("stock events must not refresh ES or Milvus")
    monkeypatch.setattr("app.sync.es_sync_consumer.SearchIndexer", forbidden)
    EsSyncConsumer(Settings())._handle_business_event(tag, {"packageId": 1})


@pytest.mark.parametrize("tag,action", [("review.created", "UPSERT"), ("review.hidden", "DELETE")])
def test_review_events_still_sync_review_and_shop(tag: str, action: str, monkeypatch: pytest.MonkeyPatch) -> None:
    calls = []
    class Indexer:
        def __init__(self, **kwargs: object) -> None:
            pass
        def sync_document(self, **kwargs: object) -> object:
            calls.append(kwargs)
            return SimpleNamespace(failed=0)
    monkeypatch.setattr("app.sync.es_sync_consumer.SearchIndexer", Indexer)
    EsSyncConsumer(Settings())._handle_business_event(tag, {"id": 2, "shopId": 1})
    assert calls[0]["scope"] == "reviews"
    assert calls[0].get("action", "UPSERT") == action
    assert calls[1] == {"scope": "shops", "entity_id": 1}


@pytest.mark.parametrize("failed_scope", [None, "reviews", "shops"])
def test_review_projection_acknowledges_only_after_both_writes(failed_scope, monkeypatch) -> None:
    class Indexer:
        def __init__(self, **kwargs):
            pass

        def sync_document(self, **kwargs):
            return SimpleNamespace(failed=int(kwargs["scope"] == failed_scope))

    monkeypatch.setattr("app.sync.es_sync_consumer.SearchIndexer", Indexer)
    acknowledgments = []
    channel = SimpleNamespace(
        basic_ack=lambda **kwargs: acknowledgments.append(("ack", kwargs)),
        basic_nack=lambda **kwargs: acknowledgments.append(("nack", kwargs)),
    )
    EsSyncConsumer(Settings())._consume(
        channel, SimpleNamespace(delivery_tag=7), SimpleNamespace(message_id="review-2"),
        json.dumps({"tag": "review.created", "payload": {"id": 2, "shopId": 1}}).encode(), "business",
    )
    expected = ("ack", {"delivery_tag": 7}) if failed_scope is None else (
        "nack", {"delivery_tag": 7, "requeue": False})
    assert acknowledgments == [expected]

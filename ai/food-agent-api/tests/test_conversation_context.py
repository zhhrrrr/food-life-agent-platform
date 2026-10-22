import json

from app.core.config import Settings
from app.memory.query_context import build_query_context
from app.memory.session_memory import SessionTurn
from app.recommend.chain import RecommendChain
from app.trade.chain import TradeChain
from app.trade.gateway_client import GatewayQueryResult


class PackageGateway:
    def __init__(self) -> None:
        self.package_ids: list[int] = []

    def get_package_snapshot(self, package_id: int) -> GatewayQueryResult:
        self.package_ids.append(package_id)
        return GatewayQueryResult(
            capability="package_snapshot",
            status="success",
            data={"packageId": package_id, "stock": 6},
            reference_id=f"package:{package_id}:trade-snapshot",
            reference_title=f"package {package_id}",
        )


def test_follow_up_query_uses_recent_turn_for_retrieval() -> None:
    history = [
        SessionTurn(
            user="推荐两款川菜双人套餐",
            assistant="可以比较套餐 11 和套餐 22。",
            intent="recommend",
        )
    ]
    context = build_query_context("第二个适合约会吗", history)

    rewrite = RecommendChain(
        settings=Settings(llm_router_enabled=False)
    ).rewrite_query("第二个适合约会吗", context)

    assert bool(context.history_prompt) is True
    assert "推荐两款川菜双人套餐" in rewrite.search_query
    assert "第二个适合约会吗" in rewrite.search_query
    assert "scene_recommendation" in rewrite.capabilities


def test_explicit_new_query_does_not_mix_old_history() -> None:
    history = [SessionTurn(user="退款规则", assistant="规则说明", intent="support")]

    context = build_query_context("推荐一家火锅店", history)

    assert json.loads(context.history_prompt)["recent_turns"][0]["user"] == "退款规则"
    assert context.follow_up is False
    assert context.retrieval_query == "推荐一家火锅店"


def test_trade_follow_up_resolves_ordinal_package_id_from_history() -> None:
    gateway = PackageGateway()
    history = [
        SessionTurn(
            user="推荐两个套餐",
            assistant="第一个是套餐 11，第二个是套餐 22。",
            intent="recommend",
        )
    ]

    result = TradeChain(
        settings=Settings(llm_router_enabled=False),
        gateway_client=gateway,  # type: ignore[arg-type]
    ).run("第二个库存呢", session_history=history)

    assert gateway.package_ids == [22]
    assert result.facts[0]["data"] == {"packageId": 22, "stock": 6}

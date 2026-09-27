from collections import Counter
from dataclasses import dataclass
from typing import Literal

SemanticIntent = Literal["recommend", "trade", "support"]

MIN_EXAMPLES_PER_INTENT = 35
MIN_EXAMPLES_PER_CAPABILITY = 6
REQUIRED_ROUTE_CAPABILITIES = {
    "recommend": frozenset(
        {
            "shop_recommendation",
            "package_recommendation",
            "cuisine_filter",
            "budget_filter",
            "scene_recommendation",
            "review_semantic_search",
            "shop_comparison",
        }
    ),
    "trade": frozenset(
        {
            "price_query",
            "stock_query",
            "coupon_query",
            "group_buy_query",
            "order_query",
            "order_preview",
            "transaction_action",
        }
    ),
    "support": frozenset(
        {
            "refund_rule",
            "group_buy_rule",
            "payment_rule",
            "order_status_explanation",
            "coupon_rule",
            "platform_faq",
        }
    ),
}
REQUIRED_ROUTE_BOUNDARIES = frozenset(
    {
        "recommend_vs_trade",
        "recommend_vs_support",
        "trade_vs_support",
        "trade_action_vs_support_rule",
    }
)


@dataclass(frozen=True, slots=True)
class RouteExample:
    intent: SemanticIntent
    capability: str
    text: str
    boundary: str = ""


@dataclass(frozen=True, slots=True)
class RouteExampleGroup:
    intent: SemanticIntent
    capability: str
    texts: tuple[str, ...]
    boundary: str = ""


ROUTE_EXAMPLE_GROUPS = (
    RouteExampleGroup(
        "recommend",
        "shop_recommendation",
        (
            "推荐几家附近好吃的餐厅",
            "不知道吃什么帮我挑一家店",
            "这附近有什么值得去的馆子",
            "想换个新餐厅尝尝有什么推荐",
            "给我找一家评分不错的饭店",
            "周末出去吃饭选哪家店好",
            "有没有本地人常去的餐馆",
        ),
    ),
    RouteExampleGroup(
        "recommend",
        "package_recommendation",
        (
            "推荐一个适合两个人吃的套餐",
            "有什么性价比高的双人餐",
            "三个人聚餐选哪个套餐合适",
            "帮我挑一个菜量足的家庭套餐",
            "想吃团购套餐有什么推荐",
            "哪种套餐比较适合第一次到店",
            "给我推荐一个包含招牌菜的套餐",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "recommend",
        "cuisine_filter",
        (
            "推荐一家正宗川菜馆",
            "附近有什么好吃的火锅店",
            "想吃粤菜帮我找一家",
            "有没有口味清淡一点的餐厅",
            "想找一家日料店",
            "推荐适合吃烧烤的地方",
            "不吃辣有什么菜系和餐厅推荐",
        ),
    ),
    RouteExampleGroup(
        "recommend",
        "budget_filter",
        (
            "预算两百元推荐一个双人套餐",
            "人均五十左右有什么餐厅",
            "想找便宜实惠的午餐",
            "三个人预算三百怎么选",
            "一百元以内能吃什么套餐",
            "不要太贵但想吃得好一点",
            "按人均预算帮我筛选几家店",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "recommend",
        "scene_recommendation",
        (
            "推荐一家适合朋友聚餐的川菜馆",
            "想找适合约会而且环境安静的餐厅",
            "带小朋友吃饭去哪比较合适",
            "公司团建聚餐推荐什么地方",
            "想给父母过生日选一家餐厅",
            "找一个适合商务请客的包间",
            "一个人吃饭有什么合适的小店",
            "深夜下班以后去哪里吃宵夜",
        ),
    ),
    RouteExampleGroup(
        "recommend",
        "review_semantic_search",
        (
            "哪家店的服务评价比较好",
            "想找大家都说环境安静的餐厅",
            "看看评价里哪家火锅味道正宗",
            "有没有口碑好而且上菜快的店",
            "找评价中提到停车方便的餐厅",
            "推荐顾客反馈菜量足的套餐",
            "哪些店最近的差评比较少",
        ),
        "recommend_vs_support",
    ),
    RouteExampleGroup(
        "recommend",
        "shop_comparison",
        (
            "比较这两家餐厅哪家口碑更好",
            "A 店和 B 店哪家更适合约会",
            "这几家火锅店哪个性价比高",
            "帮我对比两家店的环境和服务",
            "两个套餐相比哪个更值得买",
            "同样预算应该选哪一家餐厅",
            "比较候选商家的评价和特色菜",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "trade",
        "price_query",
        (
            "套餐 12 现在多少钱",
            "查询这个套餐当前售价",
            "这个双人餐今天的价格是多少",
            "帮我查一下套餐编号 88 的实时价格",
            "这个套餐现在有没有调价",
            "package 25 当前卖多少钱",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "trade",
        "stock_query",
        (
            "套餐 12 现在还有多少库存",
            "查询这个套餐当前剩余数量",
            "这个团购餐还有货吗",
            "套餐编号 66 今天还能买吗",
            "帮我查实时库存和余量",
            "刚才那个套餐现在还有几份",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "trade",
        "coupon_query",
        (
            "查询我可以使用的优惠券",
            "我的账户有哪些可用优惠券",
            "现在有什么优惠券可以领取",
            "帮我看看已经领取的券",
            "我有哪些还没使用的代金券",
            "当前可领取的折扣券有哪些",
            "结算这个套餐能用哪些券",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "trade",
        "group_buy_query",
        (
            "查看这个套餐当前可参加的拼团",
            "套餐 18 有正在拼的队伍吗",
            "查询拼团队伍 team_2024 的进度",
            "我的拼团订单现在怎么样了",
            "还有哪些拼团可以直接加入",
            "这个团还差几个人成团",
            "帮我查我参加过的拼团",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "trade",
        "order_query",
        (
            "查询订单详情和当前进度",
            "订单 12345 当前是什么状态",
            "帮我查看订单编号 9988",
            "我的订单列表有哪些",
            "刚下的订单现在到哪一步了",
            "order id 7788 的实时状态",
            "订单 66 有没有支付成功",
            "查询我最近的十个订单",
            "这个订单现在还能不能使用",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "trade",
        "order_preview",
        (
            "预览套餐 12 的订单金额",
            "买两份这个套餐先算一下多少钱",
            "看看结算预览但先不要下单",
            "套餐 35 数量三份的预估金额",
            "把套餐和候选优惠券做个订单预览",
            "计算折扣前价格不要创建订单",
        ),
        "recommend_vs_trade",
    ),
    RouteExampleGroup(
        "trade",
        "transaction_action",
        (
            "帮我创建一个订单",
            "我要立即下单",
            "取消订单 12345",
            "帮我把刚才的订单撤销",
            "支付订单 9988",
            "帮我申请订单退款",
            "把订单 66 退掉",
            "现在就帮我完成付款",
        ),
        "trade_action_vs_support_rule",
    ),
    RouteExampleGroup(
        "support",
        "refund_rule",
        (
            "平台退款规则是什么",
            "退款一般需要多长时间到账",
            "哪些情况可以申请售后",
            "退款金额是怎么计算的",
            "过期套餐还能不能退款",
            "想了解退款和售后流程",
            "优惠券抵扣部分退款时怎么算",
        ),
        "trade_action_vs_support_rule",
    ),
    RouteExampleGroup(
        "support",
        "group_buy_rule",
        (
            "拼团规则是什么",
            "拼团失败以后钱会怎么退",
            "还差一个人没成团会怎样",
            "拼团需要在多长时间内成团",
            "退出拼团有什么规定",
            "成团失败优惠券会退回来吗",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "support",
        "payment_rule",
        (
            "支付失败应该怎么处理",
            "钱扣了但订单没支付成功怎么办",
            "平台支持哪些付款方式",
            "重复支付会自动退款吗",
            "支付超时以后订单怎么处理",
            "付款页面关闭后还能继续支付吗",
            "银行卡扣款异常应该联系谁",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "support",
        "order_status_explanation",
        (
            "订单状态中的待使用是什么意思",
            "待支付状态代表什么",
            "已关闭订单是什么意思",
            "订单为什么会显示已取消",
            "已完成和已使用有什么区别",
            "订单状态一共有哪几种",
            "退款中这个状态是什么意思",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "support",
        "coupon_rule",
        (
            "平台优惠券的使用规则是什么",
            "优惠券为什么不能叠加使用",
            "代金券过期以后还能恢复吗",
            "优惠券有哪些使用门槛",
            "退款以后使用的券会返还吗",
            "一张订单最多能用几张券",
        ),
        "trade_vs_support",
    ),
    RouteExampleGroup(
        "support",
        "platform_faq",
        (
            "在哪里能找到平台帮助",
            "怎么联系人工客服",
            "账号登录遇到问题怎么办",
            "平台常见问题有哪些",
            "商家不接电话应该怎么处理",
            "使用这个平台需要注意什么",
        ),
    ),
)


ROUTE_EXAMPLES = tuple(
    RouteExample(
        intent=group.intent,
        capability=group.capability,
        text=text,
        boundary=group.boundary,
    )
    for group in ROUTE_EXAMPLE_GROUPS
    for text in group.texts
)


def validate_route_examples(
    examples: tuple[RouteExample, ...] = ROUTE_EXAMPLES,
) -> None:
    normalized_texts: set[str] = set()
    intent_counts: Counter[str] = Counter()
    capability_counts: Counter[tuple[str, str]] = Counter()

    for example in examples:
        normalized = " ".join(example.text.casefold().split())
        if not normalized:
            raise ValueError("semantic route example text must not be blank")
        if normalized in normalized_texts:
            raise ValueError(f"duplicate semantic route example: {example.text}")
        normalized_texts.add(normalized)
        intent_counts[example.intent] += 1
        capability_counts[(example.intent, example.capability)] += 1

    for intent in ("recommend", "trade", "support"):
        if intent_counts[intent] < MIN_EXAMPLES_PER_INTENT:
            raise ValueError(
                f"semantic route intent {intent} requires at least "
                f"{MIN_EXAMPLES_PER_INTENT} examples"
            )

        actual_capabilities = {
            capability
            for example_intent, capability in capability_counts
            if example_intent == intent
        }
        missing_capabilities = REQUIRED_ROUTE_CAPABILITIES[intent] - actual_capabilities
        if missing_capabilities:
            raise ValueError(
                f"semantic route intent {intent} is missing capabilities: "
                f"{', '.join(sorted(missing_capabilities))}"
            )

    sparse_capabilities = [
        f"{intent}.{capability}={count}"
        for (intent, capability), count in capability_counts.items()
        if count < MIN_EXAMPLES_PER_CAPABILITY
    ]
    if sparse_capabilities:
        raise ValueError(
            "semantic route capabilities require at least "
            f"{MIN_EXAMPLES_PER_CAPABILITY} examples: {', '.join(sparse_capabilities)}"
        )

    actual_boundaries = {example.boundary for example in examples if example.boundary}
    missing_boundaries = REQUIRED_ROUTE_BOUNDARIES - actual_boundaries
    if missing_boundaries:
        raise ValueError(
            "semantic route corpus is missing boundaries: "
            f"{', '.join(sorted(missing_boundaries))}"
        )


validate_route_examples()

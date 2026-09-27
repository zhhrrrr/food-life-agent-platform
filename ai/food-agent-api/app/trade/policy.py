TRADE_MUTATION_KEYWORDS = (
    ("创建订单", ("创建订单", "提交订单", "立即下单", "帮我下单", "直接下单", "下单")),
    ("取消订单", ("取消订单", "撤销订单")),
    ("支付订单", ("支付订单", "立即支付", "去支付", "帮我付款", "确认付款")),
    (
        "发起退款",
        ("申请退款", "退款申请", "我要退款", "发起退款", "执行退款", "帮我退款"),
    ),
)


def detect_trade_mutation(message: str) -> str:
    normalized = message.lower()
    for action, keywords in TRADE_MUTATION_KEYWORDS:
        if any(keyword.lower() in normalized for keyword in keywords):
            return action
    return ""

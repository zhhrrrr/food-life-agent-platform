from app.core.config import Settings, get_settings
from app.search.schemas import SearchDocument


def list_rule_documents(
    limit: int | None = None,
    offset: int | None = None,
    settings: Settings | None = None,
) -> list[SearchDocument]:
    resolved_settings = settings or get_settings()
    documents = _build_rule_documents(resolved_settings)
    start = max(0, offset or 0)
    if limit is None:
        return documents[start:]
    return documents[start : start + limit]


def _rule_document(
    settings: Settings,
    document_id: str,
    title: str,
    content: str,
    category: str,
    tags: list[str],
) -> SearchDocument:
    return SearchDocument(
        id=f"rule-{document_id}",
        title=title,
        content=content,
        source=settings.es_index_rule,
        category=category,
        metadata={
            "doc_type": "platform_rule",
            "domain": category,
            "tags": tags,
            "source_type": "seeded_rule_document",
            "language": "zh-CN",
        },
    )


def _build_rule_documents(settings: Settings) -> list[SearchDocument]:
    specs = [
        (
            "refund-apply-window",
            "退款申请时效规则",
            "用户支付成功后，如套餐未核销且未超过活动或套餐规定的退款时效，可以发起退款申请。已核销、已过期且规则明确不可退、或存在风控争议的订单不自动退款，需要进入人工审核。",
            "refund",
            ["退款", "售后", "未核销", "人工审核"],
        ),
        (
            "refund-payment-route",
            "退款原路退回规则",
            "退款优先按原支付渠道退回。支付订单状态、退款单状态和第三方支付回调需要保持一致；如果支付渠道返回处理中，系统应保留退款单并等待回调或补偿任务确认最终结果。",
            "refund",
            ["退款", "支付", "原路退回", "补偿"],
        ),
        (
            "refund-coupon-return",
            "退款后的优惠券返还规则",
            "订单全额退款成功后，未过期且仍满足返还条件的优惠券可以恢复为可用。部分退款、优惠券已过期、优惠券活动已下线或风控拒绝时，不保证返还优惠券。",
            "refund",
            ["退款", "优惠券", "返还", "全额退款"],
        ),
        (
            "refund-stock-release",
            "退款库存释放规则",
            "普通套餐或活动订单退款成功后，需要释放对应套餐、拼团或秒杀库存。库存释放必须以退款单成功状态为准，避免仅凭用户申请退款就提前回滚库存。",
            "refund",
            ["退款", "库存", "套餐", "活动"],
        ),
        (
            "order-create-idempotent",
            "下单幂等规则",
            "同一用户、同一业务请求号或同一幂等键重复提交下单请求时，系统应返回已有处理结果或拒绝重复创建，避免生成重复订单、重复锁库存和重复扣优惠券。",
            "order",
            ["下单", "幂等", "重复提交", "订单"],
        ),
        (
            "order-timeout-close",
            "订单超时关闭规则",
            "待支付订单超过配置的支付时间后应自动关闭。关闭时需要释放已占用库存、恢复可返还优惠券，并保证支付成功回调与超时关闭之间的状态竞争可被幂等处理。",
            "order",
            ["订单", "超时关闭", "库存释放", "支付"],
        ),
        (
            "order-cancel-before-pay",
            "支付前取消订单规则",
            "用户在未支付状态可以主动取消订单。取消成功后订单不可继续支付；库存、优惠券和活动名额需要按业务规则回滚，取消操作需要支持重复请求幂等。",
            "order",
            ["取消订单", "待支付", "库存", "优惠券"],
        ),
        (
            "payment-callback-idempotent",
            "支付回调幂等规则",
            "支付平台可能重复通知支付结果。系统处理支付成功、支付失败或退款结果时，必须以支付单号和回调流水号做幂等控制，避免重复结算、重复发券或重复改变订单状态。",
            "payment",
            ["支付", "回调", "幂等", "结算"],
        ),
        (
            "payment-success-settlement",
            "支付成功结算规则",
            "支付成功后才能进行订单结算。结算需要确认订单仍处于可支付状态，随后更新订单、支付单、库存销量和优惠券使用记录；任一环节失败需要通过补偿任务恢复最终一致。",
            "payment",
            ["支付成功", "结算", "最终一致", "补偿"],
        ),
        (
            "coupon-receive-limit",
            "优惠券领取限制规则",
            "优惠券可以配置每人领取上限、总发行量、有效期和适用范围。用户领取时需要校验活动状态、库存余量、用户领取次数和风控条件。",
            "coupon",
            ["优惠券", "领取", "限制", "库存"],
        ),
        (
            "coupon-use-scope",
            "优惠券适用范围规则",
            "优惠券可限制适用于指定门店、套餐、活动或订单类型。下单时必须按模板规则校验门店、套餐、金额门槛和有效期，不满足条件时不能抵扣。",
            "coupon",
            ["优惠券", "适用范围", "门店", "套餐"],
        ),
        (
            "coupon-expire",
            "优惠券过期规则",
            "优惠券超过有效期后自动失效。过期扫描任务可以异步处理，但下单实时校验必须以当前时间和优惠券状态为准，不能只依赖扫描结果。",
            "coupon",
            ["优惠券", "过期", "实时校验", "扫描任务"],
        ),
        (
            "group-buy-open",
            "拼团开团规则",
            "用户购买拼团活动时可以创建新团或加入已有团。开团需要校验活动状态、活动时间、套餐库存、用户资格和成团人数配置。",
            "group_buy",
            ["拼团", "开团", "活动", "库存"],
        ),
        (
            "group-buy-join",
            "拼团参团规则",
            "用户参团前需要确认团单仍在有效期内、未满员、活动仍可售且用户未违反重复参团限制。参团成功后应占用对应活动库存。",
            "group_buy",
            ["拼团", "参团", "团单", "库存"],
        ),
        (
            "group-buy-timeout",
            "拼团超时处理规则",
            "拼团在截止时间未达到成团人数时，应关闭团单并触发退款或释放资源。超时补偿任务需要能重复执行，并避免对已成团订单误退款。",
            "group_buy",
            ["拼团", "超时", "退款", "补偿"],
        ),
        (
            "seckill-entry",
            "秒杀资格校验规则",
            "秒杀下单前需要校验活动是否开始、是否结束、库存是否充足、用户是否达到限购要求，以及请求是否通过限流和风控检查。",
            "seckill",
            ["秒杀", "资格", "限购", "限流"],
        ),
        (
            "seckill-stock",
            "秒杀库存扣减规则",
            "秒杀库存扣减必须优先保证原子性和幂等性。高并发场景下应通过缓存预扣、消息异步创建订单和补偿任务降低数据库压力。",
            "seckill",
            ["秒杀", "库存", "高并发", "异步"],
        ),
        (
            "seckill-repeat-buy",
            "秒杀重复购买规则",
            "同一用户对同一秒杀活动通常只能购买一次或受限购数量限制。重复请求需要基于用户、活动和订单状态进行拦截。",
            "seckill",
            ["秒杀", "重复购买", "限购", "订单"],
        ),
        (
            "package-use-rule",
            "套餐使用规则",
            "套餐使用规则以购买时快照为准。用户到店核销时，需要校验套餐有效期、适用门店、可用时段、是否预约和是否已退款。",
            "package",
            ["套餐", "核销", "使用规则", "快照"],
        ),
        (
            "package-stock",
            "套餐库存规则",
            "套餐库存需要区分可售库存、锁定库存和已售数量。下单锁定库存，支付成功确认销量，取消或退款释放库存。",
            "package",
            ["套餐", "库存", "锁定", "销量"],
        ),
        (
            "shop-status",
            "门店营业状态规则",
            "门店下线、休息或被逻辑删除后，不应进入推荐结果和可购买范围。已购买订单仍按订单快照和售后规则继续处理。",
            "shop",
            ["门店", "营业状态", "逻辑删除", "推荐"],
        ),
        (
            "shop-review-publish",
            "评价发布规则",
            "用户完成消费后可以发布评价。评价应关联订单或核销记录，并经过内容安全、重复评价和敏感词校验后进入展示与检索。",
            "review",
            ["评价", "内容安全", "订单", "检索"],
        ),
        (
            "shop-review-score",
            "门店评分统计规则",
            "门店评分应基于有效评价统计，过滤删除、屏蔽或风控异常评价。评分快照可以异步刷新，但推荐排序应优先使用最近的有效统计。",
            "review",
            ["评价", "评分", "快照", "推荐"],
        ),
        (
            "agent-recommend-evidence",
            "推荐回答依据规则",
            "RecommendAgent 回答门店或套餐推荐问题时，应优先引用 shop、meal_package、shop_review、activity 和 coupon 的 ES 检索结果；没有依据时必须明确提示暂未检索到依据。",
            "agent",
            ["Agent", "推荐", "依据", "RAG"],
        ),
        (
            "agent-support-evidence",
            "客服回答依据规则",
            "SupportAgent 回答规则、退款、支付、售后、优惠券等问题时，应优先检索 platform_rule 或 faq_article 文档，并在 response 中保留 references 供前端展示。",
            "agent",
            ["Agent", "客服", "规则", "FAQ"],
        ),
        (
            "es-sync-realtime",
            "ES 增量同步规则",
            "业务 MySQL 写入成功后，应在同一事务写本地 Outbox 消息。后台任务投递 RabbitMQ，Python 消费后回查 Java 当前态并更新或删除 ES 文档。",
            "search",
            ["ES", "Outbox", "RabbitMQ", "增量同步"],
        ),
        (
            "es-sync-reconcile",
            "ES 定时对账规则",
            "ES 定时对账用于修复增量消息遗漏、重复或失败造成的不一致。对账以 Java 当前态为准，分页拉取业务文档，对 ES 做 upsert、skip unchanged 和 prune stale。",
            "search",
            ["ES", "对账", "当前态", "兜底"],
        ),
        (
            "es-sync-lock",
            "ES 对账分布式锁规则",
            "多实例 FastAPI 部署时，每个实例可以启动自己的定时线程，但每轮对账必须先抢 Redis 分布式锁。抢锁失败的实例跳过本轮，抢锁成功的实例执行 reconcile 并在 finally 中释放锁。",
            "search",
            ["ES", "Redis", "分布式锁", "多实例"],
        ),
        (
            "search-source-of-truth",
            "搜索文档事实源规则",
            "业务表产生的搜索文档以 Java/MySQL 当前态为事实源；平台规则和 FAQ 不属于业务交易表，应从正式运营 Markdown、配置文档或种子规则文档进入 food_rule_index。",
            "search",
            ["搜索", "事实源", "MySQL", "规则文档"],
        ),
        (
            "manual-review",
            "人工审核规则",
            "涉及高金额退款、支付异常、重复优惠、投诉或风控命中的订单，应进入人工审核。审核结论需要写入业务记录，并触发后续退款、拒绝或补偿动作。",
            "support",
            ["人工审核", "风控", "退款", "投诉"],
        ),
        (
            "customer-complaint",
            "用户投诉处理规则",
            "用户投诉应记录问题类型、订单号、门店、诉求和证据。客服优先依据订单状态、支付状态、核销记录和平台规则处理，必要时升级人工审核。",
            "support",
            ["投诉", "客服", "订单", "证据"],
        ),
    ]

    return [
        _rule_document(
            settings=settings,
            document_id=document_id,
            title=title,
            content=content,
            category=category,
            tags=tags,
        )
        for document_id, title, content, category, tags in specs
    ]

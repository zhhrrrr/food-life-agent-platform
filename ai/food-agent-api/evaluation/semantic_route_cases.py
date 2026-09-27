from dataclasses import dataclass

from app.graph.route_examples import SemanticIntent


@dataclass(frozen=True, slots=True)
class SemanticRouteEvaluationCase:
    intent: SemanticIntent
    message: str
    previous_user: str = ""
    previous_intent: str = ""


SEMANTIC_ROUTE_EVALUATION_CASES = (
    SemanticRouteEvaluationCase("recommend", "附近适合四个人吃饭的餐厅推荐一下"),
    SemanticRouteEvaluationCase("recommend", "想吃点辣的帮我选家店"),
    SemanticRouteEvaluationCase("recommend", "人均八十以内的晚餐有什么选择"),
    SemanticRouteEvaluationCase("recommend", "找一家可以安静聊天的咖啡餐厅"),
    SemanticRouteEvaluationCase("recommend", "哪家烧烤评价里经常提到食材新鲜"),
    SemanticRouteEvaluationCase("recommend", "第一次约会去哪里吃比较好"),
    SemanticRouteEvaluationCase("recommend", "六个人聚会有什么大份套餐"),
    SemanticRouteEvaluationCase("recommend", "帮我在这三家候选餐厅里挑一个"),
    SemanticRouteEvaluationCase("recommend", "不想吃辣推荐清淡一点的菜"),
    SemanticRouteEvaluationCase("recommend", "有没有适合带老人的家庭餐厅"),
    SemanticRouteEvaluationCase("recommend", "两百块预算怎么安排三人餐"),
    SemanticRouteEvaluationCase("recommend", "想找服务好并且停车方便的店"),
    SemanticRouteEvaluationCase("recommend", "火锅和烤肉这两家哪一个口碑更稳"),
    SemanticRouteEvaluationCase("recommend", "午休只有一小时吃哪家比较快"),
    SemanticRouteEvaluationCase("recommend", "给我挑一个包含牛肉的团购餐"),
    SemanticRouteEvaluationCase("recommend", "周末带孩子庆生去哪吃"),
    SemanticRouteEvaluationCase(
        "recommend", "刚才提到的几家哪家环境更好", "推荐几家附近的西餐厅", "recommend"
    ),
    SemanticRouteEvaluationCase(
        "recommend", "换一个更便宜的", "推荐适合约会的双人套餐", "recommend"
    ),
    SemanticRouteEvaluationCase(
        "recommend", "它家评价怎么样", "我在考虑春熙路那家川菜馆", "recommend"
    ),
    SemanticRouteEvaluationCase(
        "recommend", "同样价位还有别的店吗", "推荐一个人均一百的日料店", "recommend"
    ),
    SemanticRouteEvaluationCase("trade", "查一下套餐 102 的价格和剩余份数"),
    SemanticRouteEvaluationCase("trade", "订单号 20240918 目前进展如何"),
    SemanticRouteEvaluationCase("trade", "我账户中还有什么券没过期"),
    SemanticRouteEvaluationCase("trade", "查查套餐 76 有没有可以加入的团"),
    SemanticRouteEvaluationCase("trade", "team id abc_889 还差几位成员"),
    SemanticRouteEvaluationCase("trade", "给我列出最近的订单记录"),
    SemanticRouteEvaluationCase("trade", "先算三份套餐 33 的金额不要提交"),
    SemanticRouteEvaluationCase("trade", "订单 551 是不是已经退款了"),
    SemanticRouteEvaluationCase("trade", "帮我取消编号 8877 的订单"),
    SemanticRouteEvaluationCase("trade", "我要买套餐 9 直接下单"),
    SemanticRouteEvaluationCase("trade", "现在能领到哪些满减券"),
    SemanticRouteEvaluationCase("trade", "查询 package id 19 的实时库存"),
    SemanticRouteEvaluationCase("trade", "我参加的拼团有没有成团"),
    SemanticRouteEvaluationCase("trade", "订单详情给我看一下"),
    SemanticRouteEvaluationCase("trade", "把这个订单支付掉"),
    SemanticRouteEvaluationCase("trade", "申请退掉订单 778"),
    SemanticRouteEvaluationCase(
        "trade", "那还有多少份", "查询套餐 28 当前价格", "trade"
    ),
    SemanticRouteEvaluationCase(
        "trade", "这个团现在进度呢", "我加入了拼团队伍 team_33", "trade"
    ),
    SemanticRouteEvaluationCase(
        "trade", "它支付成功了吗", "查看订单 991 的详情", "trade"
    ),
    SemanticRouteEvaluationCase(
        "trade", "那我能用什么券", "预览套餐 17 的结算金额", "trade"
    ),
    SemanticRouteEvaluationCase("support", "一般退款几个工作日能到账"),
    SemanticRouteEvaluationCase("support", "没拼成功会原路退钱吗"),
    SemanticRouteEvaluationCase("support", "待消费这个订单状态怎么理解"),
    SemanticRouteEvaluationCase("support", "付款失败但是已经扣款该如何处理"),
    SemanticRouteEvaluationCase("support", "优惠券能不能和店铺活动一起使用"),
    SemanticRouteEvaluationCase("support", "售后申请需要满足哪些条件"),
    SemanticRouteEvaluationCase("support", "成团有效时间是多久"),
    SemanticRouteEvaluationCase("support", "订单关闭通常是什么原因"),
    SemanticRouteEvaluationCase("support", "退款时满减券会不会退回账户"),
    SemanticRouteEvaluationCase("support", "怎么找到在线客服入口"),
    SemanticRouteEvaluationCase("support", "付款超时后还可以重新付吗"),
    SemanticRouteEvaluationCase("support", "平台的账号无法登录怎么解决"),
    SemanticRouteEvaluationCase("support", "解释一下已完成和待使用的区别"),
    SemanticRouteEvaluationCase("support", "拼团过程中可以退出吗"),
    SemanticRouteEvaluationCase("support", "代金券失效之后还能补发吗"),
    SemanticRouteEvaluationCase("support", "商家拒绝处理售后该怎么办"),
    SemanticRouteEvaluationCase(
        "support", "那这种情况怎么退款", "我想了解拼团失败的处理规则", "support"
    ),
    SemanticRouteEvaluationCase(
        "support", "这个状态代表什么", "订单页面显示退款处理中", "support"
    ),
    SemanticRouteEvaluationCase(
        "support", "还有其他限制吗", "平台优惠券有哪些使用门槛", "support"
    ),
    SemanticRouteEvaluationCase(
        "support", "那钱会怎么处理", "支付失败以后应该怎么办", "support"
    ),
)

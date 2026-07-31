# 开发日志-016-trade服务支付成功结算链

## 本次目标

按拼团项目里的支付成功结算逻辑做 trade-service 的普通购买支付流。已有项目里能复用的逻辑，优先迁移思路和模板，不再自己临时写一套。

本次迁移的是拼团里的 settlement 逻辑骨架：

```text
支付成功事件
  -> settlement service
  -> settlement rule filter chain
  -> 构造结算结果
  -> 原子更新订单状态
```

## 对应拼团项目逻辑

参考拼团项目：

```text
group-buy-market-domain
cn.bugstack.domain.trade.service.settlement.TradeSettlementOrderService
cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory
cn.bugstack.domain.trade.service.settlement.filter.SCRuleFilter
cn.bugstack.domain.trade.service.settlement.filter.OutTradeNoRuleFilter
cn.bugstack.domain.trade.service.settlement.filter.SettableRuleFilter
cn.bugstack.domain.trade.service.settlement.filter.EndRuleFilter
```

拼团里的核心做法是：

```text
1. 支付成功实体 TradePaySuccessEntity 进入 settlementMarketPayOrder
2. 构造 TradeSettlementRuleCommandEntity
3. 进入 tradeSettlementRuleFilter
4. 规则链校验来源、交易单、可结算状态
5. EndRuleFilter 返回 TradeSettlementRuleFilterBackEntity
6. repository.settlementMarketPayOrder 做最终结算落库
7. 返回 TradePaySettlementEntity
```

本项目本次对应改成：

```text
1. 支付成功实体 OrderPaySuccessEntity 进入 settlementOrderPaySuccess
2. 构造 OrderSettlementRuleCommandEntity
3. 进入 orderPaySettlementRuleFilter
4. 规则链校验登录用户、订单存在、订单可支付
5. OrderSettlementEndRuleFilter 返回 OrderSettlementRuleFilterBackEntity
6. repository.updateOrderStatus 做 WAIT_PAY -> PAID 原子状态更新
7. 返回 OrderPaySettlementEntity
```

## 新增接口

```text
POST /api/trade/orders/{orderId}/pay/mock
```

说明：

```text
这是本地开发阶段的模拟支付成功入口。
后续接支付宝、微信支付、s-pay 支付中心或者 Python Agent 调用时，可以把外部支付回调适配成 OrderPaySuccessEntity，再进入同一个 settlement service。
```

请求体可以为空，也可以传：

```json
{
  "source": "FOOD_LIFE",
  "channel": "MOCK_PAY",
  "outTradeNo": "MOCK202607310001"
}
```

响应示例：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "source": "FOOD_LIFE",
    "channel": "MOCK_PAY",
    "userId": 1,
    "orderId": 1,
    "orderNo": "NO17853963882542",
    "orderStatus": "PAID",
    "outTradeNo": "MOCK202607310001",
    "outTradeTime": "2026-07-31T10:30:00"
  }
}
```

## 新增文件

### API DTO

```text
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/PayOrderRequestDTO.java
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/PayOrderResponseDTO.java
```

作用：

```text
PayOrderRequestDTO：模拟支付成功请求，可以传支付来源、支付渠道、外部交易号。
PayOrderResponseDTO：返回订单支付结算后的结果。
```

### 支付成功和结算领域对象

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderPaySuccessEntity.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderPaySettlementEntity.java
```

作用：

```text
OrderPaySuccessEntity 对应拼团 TradePaySuccessEntity。
OrderPaySettlementEntity 对应拼团 TradePaySettlementEntity。
```

### 支付结算规则链对象

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderSettlementRuleCommandEntity.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderSettlementRuleFilterBackEntity.java
```

作用：

```text
OrderSettlementRuleCommandEntity 对应拼团 TradeSettlementRuleCommandEntity。
OrderSettlementRuleFilterBackEntity 对应拼团 TradeSettlementRuleFilterBackEntity。
```

### 支付结算规则链工厂

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/settlement/factory/OrderSettlementRuleFilterFactory.java
```

作用：

```text
使用已迁移的 xfg BusinessLinkedList 和 LinkArmory 组装一条支付结算规则链。
```

当前链节点：

```text
PaySuccessCommandRuleFilter
  -> OrderSettlementLoadRuleFilter
  -> OrderPayableRuleFilter
  -> OrderSettlementEndRuleFilter
```

### 支付结算规则链节点

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/settlement/filter/PaySuccessCommandRuleFilter.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/settlement/filter/OrderSettlementLoadRuleFilter.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/settlement/filter/OrderPayableRuleFilter.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/settlement/filter/OrderSettlementEndRuleFilter.java
```

每个节点职责：

```text
PaySuccessCommandRuleFilter：
校验 userId、orderId、outTradeTime 是否存在。

OrderSettlementLoadRuleFilter：
根据 orderId + userId 查询订单，防止用户越权支付别人的订单。

OrderPayableRuleFilter：
校验订单状态必须是 WAIT_PAY。
已取消 CANCELED、已支付 PAID 的订单不能再次支付。

OrderSettlementEndRuleFilter：
从上下文取出订单，封装结算链返回对象。
```

### 支付结算服务

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/settlement/OrderPaySettlementService.java
```

作用：

```text
对应拼团 TradeSettlementOrderService。
负责接收支付成功实体，执行规则链，并做订单支付结算。
```

当前结算落库：

```text
dining_order.order_status: WAIT_PAY -> PAID
```

使用仓储方法：

```text
IOrderRepository.updateOrderStatus(orderId, WAIT_PAY, PAID)
```

这个更新带旧状态条件，能防止重复支付、并发支付、取消后支付。

## 修改文件

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/constant/OrderStatusConstants.java
```

新增：

```text
PAID
```

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

新增：

```text
payOrderMock(OrderPaySuccessEntity paySuccessEntity)
```

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

新增：

```text
POST /api/trade/orders/{orderId}/pay/mock
```

```text
docs/sql/food_trade_db.sql
```

更新：

```text
order_status 注释补充 WAIT_PAY/PAID/CANCELED。
```

## 业务流程

```text
1. 用户登录，Token 由黑马点评那套登录方案解析到 UserHolder
2. 用户创建普通购买订单，订单状态为 WAIT_PAY
3. 本地模拟支付成功，请求 /api/trade/orders/{orderId}/pay/mock
4. Controller 构造 OrderPaySuccessEntity
5. OrderDomainService 调用 OrderPaySettlementService
6. OrderPaySettlementService 构造 OrderSettlementRuleCommandEntity
7. 进入 orderPaySettlementRuleFilter
8. 校验支付成功命令
9. 加载当前用户自己的订单
10. 校验订单必须是 WAIT_PAY
11. End 节点返回订单结算上下文
12. 仓储层执行 WAIT_PAY -> PAID 原子状态更新
13. 返回支付结算结果
14. 查询订单详情，订单状态为 PAID
```

## 为什么这次按拼团逻辑做

这次没有把普通购买的完整业务强行抽成模板。原因还是之前确认过的：

```text
普通购买、拼团、秒杀的业务规则不同，不适合共用一个下单流程模板。
```

但是支付成功后的结算入口适合按拼团方式做：

```text
支付成功事件是统一入口。
规则链可以按业务类型扩展不同节点。
最终状态更新必须原子化。
后续接支付中心或 Python Agent 时，入口实体稳定。
```

## 当前没有做的部分

```text
1. 没有新增 payment_order 支付单表
2. 没有接支付宝、微信支付或 s-pay 支付中心
3. 没有做库存扣减
4. 没有做支付回调签名校验
5. 没有做消息通知和异步任务补偿
```

原因：

```text
当前 trade-service 还处在普通购买主流程阶段。
先把拼团的支付成功结算链路迁移好，再继续接支付单、库存、消息任务会更稳。
```

## 验证结果

已执行：

```bash
mvn -q test
```

结果：

```text
通过。
```

已继续完成本地接口联调：

```text
登录 -> 下单 -> 支付 -> 查询详情：通过
重复支付失败：通过
取消后支付失败：通过
未登录支付失败：通过
越权支付失败：通过
```

实测记录：

```text
主链路：
订单 10 创建后为 WAIT_PAY，支付后为 PAID，详情查询也是 PAID。

重复支付：
订单 11 首次支付成功，第二次支付返回 code=400，message=order status can not pay。

取消后支付：
订单 12 取消后状态为 CANCELED，再支付返回 code=400，message=order status can not pay。

未登录支付：
返回 code=401，message=用户未登录。

越权支付：
用户 13 支付用户 12 的订单 11，返回 code=400，message=order not found。
```

打包记录：

```text
mvn -q package -DskipTests
```

整仓打包时因为 Windows 正在运行的 user-service jar 被锁定，repackage 无法重命名 jar。

改为只打包 trade-service：

```text
mvn -q package -DskipTests -pl food-trade-service/food-trade-app -am
```

结果：

```text
通过。
```

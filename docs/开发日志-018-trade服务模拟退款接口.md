# 开发日志-018-trade服务模拟退款接口

## 本次目标

新增普通购买订单的模拟退款能力。

真实支付和真实退款后续接支付中心时再处理，本次只做订单逆向流程的业务骨架。

## 对应现成项目逻辑

参考 `s-pay-mall-ddd-market`：

```text
s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/OrderService.java
```

其中 `refundPayOrder(userId, orderId)` 的核心逻辑：

```text
1. 根据 userId + orderId 查询订单
2. 校验订单必须存在且属于当前用户
3. 调用支付宝退款
4. 仓储更新订单为关闭/退款状态
```

参考 `group-buy-market`：

```text
group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/refund/TradeRefundOrderService.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/refund/factory/TradeRefundRuleFilterFactory.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/refund/filter/DataNodeFilter.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/refund/filter/UniqueRefundNodeFilter.java
group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/refund/filter/RefundOrderNodeFilter.java
```

拼团退单链：

```text
DataNodeFilter
  -> UniqueRefundNodeFilter
  -> RefundOrderNodeFilter
```

本项目迁移后：

```text
RefundOrderLoadRuleFilter
  -> UniqueRefundRuleFilter
  -> RefundOrderRuleFilter
```

## 新增接口

```text
POST /api/trade/orders/{orderId}/refund/mock
```

请求体可以为空，也可以传：

```json
{
  "source": "FOOD_LIFE",
  "channel": "MOCK_REFUND"
}
```

## 新增订单状态

```text
REFUNDED
```

当前普通购买订单状态：

```text
WAIT_PAY
PAID
CANCELED
REFUNDED
```

当前状态流转：

```text
下单：null -> WAIT_PAY
取消：WAIT_PAY -> CANCELED
模拟支付：WAIT_PAY -> PAID
模拟退款：PAID -> REFUNDED
重复退款：REFUNDED -> REFUNDED，返回 repeat
```

## 新增文件

### API DTO

```text
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/RefundOrderRequestDTO.java
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/RefundOrderResponseDTO.java
```

### 领域对象

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderRefundCommandEntity.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderRefundBehaviorEntity.java
```

### 退单规则链

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/factory/OrderRefundRuleFilterFactory.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/filter/RefundOrderLoadRuleFilter.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/filter/UniqueRefundRuleFilter.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/filter/RefundOrderRuleFilter.java
```

### 退单服务

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/refund/OrderRefundService.java
```

## 业务规则

```text
1. 用户必须登录
2. 订单必须存在
3. 订单必须属于当前登录用户
4. 只有 PAID 状态可以退款
5. REFUNDED 状态重复退款返回 repeat
6. WAIT_PAY 不能退款，应该走取消订单
7. CANCELED 不能退款
```

## 当前没有做的部分

```text
1. 没有调用支付宝/微信/支付中心真实退款
2. 没有新增退款单表
3. 没有退款回调通知
4. 没有退款补偿任务
5. 没有拼团库存恢复
```

原因：

```text
真实支付骨架后续统一接。
当前普通购买没有拼团团队和锁单库存上下文。
本次先把订单逆向状态流转打通。
```

## 验证结果

```text
mvn -q test
```

结果：

```text
通过。
```

已完成接口联调：

```text
登录 -> 下单 -> 支付 -> 退款：通过
重复退款：通过
待支付订单退款：通过
已取消订单退款：通过
未登录退款：通过
越权退款：通过
```

实测记录：

```text
主链路：
订单 13 创建后 WAIT_PAY，支付后 PAID，退款后 REFUNDED，详情查询也是 REFUNDED。

重复退款：
订单 13 第二次退款返回 code=0000，refundBehavior=repeat，orderStatus=REFUNDED。

待支付订单退款：
订单 14 未支付退款返回 code=400，message=order status can not refund。

已取消订单退款：
订单 15 取消后退款返回 code=400，message=order status can not refund。

越权退款：
用户 13 退用户 12 的订单 13，返回 code=400，message=order not found。

未登录退款：
HTTP 401
{"code":"401","message":"用户未登录"}
```

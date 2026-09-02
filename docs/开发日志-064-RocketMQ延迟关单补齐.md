# 开发日志 064：RocketMQ 延迟关单补齐

## 本次目标

第五阶段 RocketMQ 改造里，原先已经把订单创建、支付、库存、评价事件接入了 MQ 发布模型。

本次补齐的是更贴近生产的“延迟关单”链路：

```text
下单成功
  -> 发送 trade_order_topic:order.cancel.timeout 延迟消息
  -> RocketMQ 到期投递
  -> 消费者查询订单
  -> 订单仍是 WAIT_PAY 才关闭订单
  -> 释放库存/优惠券/拼团占位/秒杀占位
  -> 发布真正的 order.cancel.timeout 业务结果事件
```

## 为什么要补这一块

之前的实现更多是“已有定时任务关单后发布事件”，它能记录事件，但面试讲“RocketMQ 延迟消息关闭订单”时还不够完整。

这次改完后，可以讲成：

- 创建订单后立即投递延迟消息
- 延迟消息到期后由消费者执行状态检查
- 只关闭仍未支付的订单
- 支付成功、已核销、已取消等状态天然跳过
- 取消动作复用原来的 `cancelOrder`，不重新写一套业务规则

## 新增领域模型

位置：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/event/
```

新增：

- `OrderTimeoutCloseMessage`
  - 延迟消息体
  - 包含 orderId、orderNo、userId、tradeType、orderCreateTime

- `OrderTimeoutCloseResult`
  - 消费处理结果
  - 记录关闭前后订单状态、支付状态、是否关闭支付单、是否取消订单、跳过原因

- `OrderTimeoutDelayCloseService`
  - 真正的延迟关单领域服务
  - 到期后先查订单
  - 订单不是 `WAIT_PAY` 直接跳过
  - 如果有 `PREPARED` 支付单，先关闭支付单
  - 再复用 `OrderDomainService.cancelOrder`
  - 成功后发布 `order.cancel.timeout` 结果事件

## 发布器改造

位置：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/event/ITradeEventPublisher.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRocketMqEventPublisher.java
```

新增能力：

```java
void publishDelay(String topic, String tag, String key, Object payload);
```

基础设施实现：

- 普通消息：不设置 delayLevel
- 延迟消息：从配置读取 `food.mq.order-timeout-delay-level`
- 本地可靠消息表继续保存消息
- RocketMQ 开启时调用 `Message#setDelayTimeLevel`
- RocketMQ 关闭时仍然走本地 mock-success，保证本地开发不被 MQ server 卡住

默认配置：

```yaml
food:
  mq:
    order-timeout-delay-level: 16
```

RocketMQ 4.x 默认延迟等级里，16 通常对应 30 分钟。

## 下单主流程改造

位置：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

普通购买：

```text
保存订单
  -> 保存订单明细
  -> 发布 order.created
  -> 发布延迟 order.cancel.timeout，key=timeout-close:{orderId}
```

拼团锁单：

```text
拼团锁单成功
  -> 发布 order.created
  -> 发布延迟 order.cancel.timeout，key=timeout-close:{orderId}
```

同步秒杀：

```text
秒杀下单成功
  -> 发布 order.created
  -> 发布延迟 order.cancel.timeout，key=timeout-close:{orderId}
```

异步秒杀：

位置：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/service/SeckillOrderService.java
```

```text
秒杀请求消息处理成功
  -> 真实创建订单
  -> 标记请求成功
  -> 发布 order.created
  -> 发布延迟 order.cancel.timeout，key=timeout-close:{orderId}
```

## 消费者改造

位置：

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeOrderTimeoutCloseConsumer.java
```

职责：

- 只在 `food.mq.enabled=true` 时启动
- 订阅 `trade_order_topic`
- 只消费 `order.cancel.timeout`
- 解析本地统一事件结构中的 `payload`
- 调用 `OrderTimeoutDelayCloseService.closeTimeoutOrder`
- 处理失败返回 `RECONSUME_LATER`

## 幂等与一致性

这条链路的幂等点不是额外建表，而是复用订单状态机：

```text
WAIT_PAY -> CANCELED
```

消费者重复投递时：

- 如果订单已经 `PAID`，跳过
- 如果订单已经 `USED`，跳过
- 如果订单已经 `CANCELED`，跳过
- 如果支付单已经不是 `PREPARED`，不重复关闭支付单

这符合 RocketMQ 至少一次投递模型。

## 配置变更

Nacos：

```text
deploy/nacos/configs/food-trade-service.yaml
```

本地兜底：

```text
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

新增：

```yaml
food:
  mq:
    order-timeout-consumer-group: food-trade-order-timeout-consumer
    order-timeout-delay-level: 16
```

## 验证补充

冒烟脚本新增断言：

```text
scripts/smoke-rocketmq-events.ps1
```

现在除了验证订单创建、支付、核销、评价消息，还会检查：

```sql
trade_order_topic:order.cancel.timeout:timeout-close:{orderId}
```

确保下单成功后确实生成了延迟关单消息。

# 数据记录-RocketMQ消息治理

## 配置总览

公共配置：

```text
deploy/nacos/configs/food-common.yaml
```

```yaml
food:
  mq:
    enabled: ${FOOD_MQ_ENABLED:false}
    name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
```

trade-service：

```text
deploy/nacos/configs/food-trade-service.yaml
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

```yaml
food:
  mq:
    producer-group: food-trade-service-producer
    retry-delay-seconds: 30
    retry-limit: 50
  jobs:
    trade-event-retry:
      enabled: true
      fixed-delay-ms: 30000
      limit: 50
```

business-service：

```text
deploy/nacos/configs/food-business-service.yaml
food-business-service/food-business-app/src/main/resources/application-local.yml
```

```yaml
food:
  mq:
    producer-group: food-business-service-producer
    review-consumer-group: food-business-review-consumer
```

## Topic 设计

### trade_order_topic

归属：trade-service

```text
order.created
order.cancel.timeout
order.paid
order.refund.requested
order.used
```

用途：

```text
订单创建、支付、取消、退款、核销等订单生命周期事件。
```

### package_stock_topic

归属：business-service

```text
stock.occupied
stock.released
stock.sold.confirmed
stock.rollback
```

用途：

```text
套餐库存占用、释放、销量确认、销量回滚事件。
```

### payment_topic

归属：trade-service

```text
payment.created
payment.success
payment.closed
payment.refunded
```

用途：

```text
支付单创建、支付成功、支付关闭、支付退款事件。
```

### shop_review_topic

归属：business-service

```text
review.created
```

用途：

```text
评价创建后异步更新店铺评分摘要。
```

## trade 本地消息表

表：

```text
food_trade_db.trade_local_message
```

RocketMQ 事件固定使用：

```text
biz_type = TRADE_EVENT
```

message_id 生成规则：

```text
{topic}:{tag}:{key}
```

示例：

```text
trade_order_topic:order.created:81
payment_topic:payment.created:PAY17883634054923d136688
```

状态：

```text
INIT        待发送/待重试
PROCESSING 发送中
SUCCESS     已发送
FAILED      超过最大重试次数
```

## business 消费幂等表

表：

```text
food_business_db.business_consumed_message
```

DDL：

```text
docs/sql/food_business_db_migration_063_business_consumed_message.sql
```

唯一键：

```text
uk_message_id(message_id)
```

作用：

```text
RocketMQ 是至少一次投递，消费者可能收到重复消息。
消费者处理 review.created 前先插入 message_id。
插入成功才更新 shop.comments / shop.score。
插入失败说明已经消费过，直接返回成功，避免重复累加评分。
```

## 当前本地模式

当前默认：

```text
food.mq.enabled=false
```

含义：

```text
不连接 RocketMQ nameserver
trade 事件写入 trade_local_message 后标记 SUCCESS
business review.created 走本地 fallback 更新评分摘要
```

这种模式适合本地开发和无 MQ Server 的联调。

## 真实 RocketMQ 模式

开启方式：

```powershell
$env:FOOD_MQ_ENABLED="true"
$env:ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
scripts/start-local-services-nacos.ps1 -Rebuild -Restart
```

或者在 Nacos 中修改：

```yaml
food:
  mq:
    enabled: true
    name-server: 127.0.0.1:9876
```

要求：

```text
本机或远程 RocketMQ nameserver 与 broker 已经启动。
```

## 面试问题对应

为什么用 MQ：

```text
削峰填谷、服务解耦、异步化、最终一致性、减少同步调用链长度。
```

同步调用和异步消息区别：

```text
同步调用要求调用方等待结果，链路简单但耦合高。
异步消息只保证事件投递，消费者自行处理，链路解耦但需要处理重复、丢失、顺序、积压。
```

重复消费：

```text
用业务唯一键和消费幂等表处理。
本项目 business_consumed_message.uk_message_id 解决 review.created 重复消费。
```

消息丢失：

```text
trade-service 先写 trade_local_message，再发送 MQ。
发送失败不会直接丢，补偿任务继续扫描 INIT 消息重发。
```

消息积压：

```text
当前做了 retry limit 和批量 limit。
生产可继续做消费者水平扩容、按 key 分队列、慢消费监控、死信队列和降级开关。
```

顺序消息：

```text
同一订单使用 orderId 作为 key。
生产要使用 MessageQueueSelector 按 orderId 路由到同一队列，保证单订单内顺序。
当前骨架保留了 key，下一步可以升级为顺序发送。
```

延迟消息：

```text
当前订单超时仍由定时补偿扫描。
生产可以把 order.created 后发送延迟消息，到期后消费 order.cancel.timeout，检查订单仍 WAIT_PAY 再关闭。
```

事务消息：

```text
事务消息解决本地事务和 MQ 发送的一致性问题。
RocketMQ 事务消息通过半消息、执行本地事务、提交/回滚、事务回查保证最终状态。
```

本地消息表和 RocketMQ 事务消息区别：

```text
本地消息表由业务库保存消息，靠定时任务补偿发送，简单直观，适合讲清楚可靠消息最终一致性。
RocketMQ 事务消息由 RocketMQ 协议管理半消息和事务回查，对 MQ 依赖更强，实时性更好，但实现和运维复杂度更高。
```


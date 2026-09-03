# 数据记录-RabbitMQ消息治理

## 当前 MQ 技术栈

当前项目使用 RabbitMQ，接入方式是 Spring AMQP：

```xml
org.springframework.boot:spring-boot-starter-amqp
```

本地默认不开真实 MQ：

```yaml
food:
  mq:
    enabled: false
```

打开真实 MQ 后，trade-service 和 business-service 才声明 RabbitMQ exchange、queue、binding，并启用 listener。

## RabbitMQ 连接配置

公共配置在：

```text
deploy/nacos/configs/food-common.yaml
```

配置项：

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:127.0.0.1}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: ${RABBITMQ_VIRTUAL_HOST:/}
food:
  mq:
    enabled: ${FOOD_MQ_ENABLED:false}
```

本地 profile 对应配置：

- `food-trade-service/food-trade-app/src/main/resources/application-local.yml`
- `food-business-service/food-business-app/src/main/resources/application-local.yml`

## trade-service 消息模型

### Exchange

```text
trade_order_topic
payment_topic
```

为了少改领域层，仍沿用 `topic/tag` 命名：

- topic 对应 RabbitMQ exchange
- tag 对应 RabbitMQ routing key

### Routing Key

```text
order.created
order.cancel.timeout
order.paid
order.refund.requested
order.used
payment.created
payment.success
payment.closed
payment.refunded
```

### Queue

```text
food.trade.order.timeout.delay.queue
food.trade.order.timeout.close.queue
food.trade.order.event.queue
food.trade.payment.event.queue
```

### 延迟关单路由

```text
trade_order_topic / order.cancel.timeout.delay
  -> food.trade.order.timeout.delay.queue
  -> TTL 到期
  -> DLX: trade_order_topic / order.cancel.timeout
  -> food.trade.order.timeout.close.queue
  -> TradeOrderTimeoutCloseListener
```

配置：

```yaml
food:
  mq:
    order-timeout-close-queue: food.trade.order.timeout.close.queue
    order-timeout-delay-queue: food.trade.order.timeout.delay.queue
    order-timeout-delay-routing-key: order.cancel.timeout.delay
    order-timeout-delay-millis: 1800000
```

## business-service 消息模型

### Exchange

```text
package_stock_topic
shop_review_topic
```

### Routing Key

```text
stock.occupied
stock.released
stock.sold.confirmed
stock.rollback
review.created
```

### Queue

```text
food.business.review.created.queue
food.business.package.stock.event.queue
```

### 评价异步更新评分

```text
shop_review_topic / review.created
  -> food.business.review.created.queue
  -> BusinessReviewCreatedListener
  -> IShopReviewRepository.applyReviewCreatedStats
  -> business_consumed_message 幂等
```

## 本地消息表

trade-service 使用：

```text
food_trade_db.trade_local_message
```

用途：

- 下单、支付、核销、退款等事件先落本地消息表
- RabbitMQ 开启时再投递
- 投递失败后定时重试
- 避免业务数据库提交成功但消息直接丢失

重试任务：

```text
TradeRabbitMqEventRetryJob
```

配置：

```yaml
food:
  jobs:
    trade-event-retry:
      enabled: true
      fixed-delay-ms: 30000
      limit: 50
```

## 消费幂等表

business-service 使用：

```text
food_business_db.business_consumed_message
```

用途：

- 记录已消费的评价事件
- 通过 message_id 唯一索引避免重复更新店铺评分摘要

字段语义：

- `topic`：RabbitMQ exchange
- `tag`：RabbitMQ routing key
- `biz_key`：业务 key，例如 reviewNo
- `consume_status`：消费状态

## 常见面试问题

### 为什么要用 MQ

订单、支付、库存、评价这些动作互相有关，但不应该全部强同步串在一起。

MQ 的价值：

- 削峰
- 解耦
- 异步化
- 最终一致性
- 失败重试
- 延迟关单

### 怎么防消息丢失

当前项目使用本地消息表：

```text
业务落库成功
  -> 本地消息表 INIT
  -> 投递成功改 SUCCESS
  -> 投递失败保留 INIT/FAILED
  -> 定时任务重试
```

生产环境还可以继续补：

- RabbitMQ publisher confirm
- return callback
- 消息持久化
- 告警和人工补偿后台

### 怎么防重复消费

消费者按 messageId 做幂等。

当前评价事件：

```text
review.created eventId = topic:tag:reviewNo
```

消费前写入或检查：

```text
business_consumed_message
```

重复消息不会重复更新店铺评分。

### 延迟消息怎么做

当前使用 TTL + DLX：

```text
delay queue 设置死信交换机
消息设置 expiration
到期后 RabbitMQ 自动转发到 close queue
```

### RabbitMQ 和 RocketMQ 延迟消息区别

RabbitMQ：

- 标准方案是 TTL + DLX
- 也可以装 delayed-message 插件
- 运维简单，适合业务事件和常规异步化

RocketMQ：

- 4.x 常用 delayLevel
- 5.x 支持更完整的延迟消息能力
- 更适合大规模日志、交易消息、顺序消息场景

### 本地消息表和事务消息区别

本地消息表：

- 更通用
- 不绑定具体 MQ
- 需要重试任务扫描
- 实时性略弱

事务消息：

- 由 MQ 协议管理半消息、提交、回滚、回查
- 实时性更好
- 对 MQ 技术栈依赖更强

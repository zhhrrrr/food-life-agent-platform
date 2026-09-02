# 开发日志-063-RocketMQ异步化与最终一致性

## 本次目标

第五阶段引入 RocketMQ 异步化和最终一致性能力。

本次不是直接把所有同步链路拆掉，而是在现有稳定业务上新增事件层：

- 订单、支付、库存、评价产生业务事件
- trade-service 使用本地消息表保证事件可靠发布
- business-service 使用消费幂等表保证评价摘要不会重复累加
- 默认关闭真实 RocketMQ 连接，本地仍可完整启动和联调
- 打开 `food.mq.enabled=true` 后可以连接 RocketMQ nameserver 进行真实投递

## 改造原则

### 1. DDD 分层

domain 层只定义事件发布接口和业务事件常量：

```text
ITradeEventPublisher
IBusinessEventPublisher
TradeMqTopics
BusinessMqTopics
```

infrastructure 层才负责 RocketMQ client、消息发送、消费、幂等表。

这样领域层不依赖 RocketMQ 技术实现，后续换 Kafka、RabbitMQ 或事务消息组件时，领域业务不需要重写。

### 2. 默认本地可运行

当前配置：

```yaml
food:
  mq:
    enabled: false
    name-server: 127.0.0.1:9876
```

本地没有启动 RocketMQ 时：

- 服务不会连接 nameserver
- trade 事件会写本地消息表并标记为 mock success
- business 的 `review.created` 会走本地 fallback，同步应用评分摘要

### 3. 最终一致性

trade-service 的订单/支付事件使用：

```text
业务成功
  -> 写 trade_local_message
  -> 发 RocketMQ
  -> 成功标记 SUCCESS
  -> 失败保持 INIT，等待定时补偿
```

business-service 的评价摘要使用：

```text
评价保存成功
  -> 发布 review.created
  -> 消费者更新店铺评分摘要
  -> business_consumed_message 唯一键防重复消费
```

## Topic 和 Tag

### trade_order_topic

```text
order.created
order.cancel.timeout
order.paid
order.refund.requested
order.used
```

### package_stock_topic

```text
stock.occupied
stock.released
stock.sold.confirmed
stock.rollback
```

### payment_topic

```text
payment.created
payment.success
payment.closed
payment.refunded
```

### shop_review_topic

```text
review.created
```

`shop_review_topic` 是本次为了评价摘要异步更新额外增加的业务 Topic。

## 新增与修改文件

### 1. RocketMQ client 依赖

```text
pom.xml
food-trade-service/food-trade-infrastructure/pom.xml
food-business-service/food-business-infrastructure/pom.xml
```

新增：

```text
org.apache.rocketmq:rocketmq-client:4.9.7
```

本次使用 RocketMQ 原生 client，不直接上 starter，是为了避免没有 RocketMQ Server 时自动配置强依赖启动失败。

### 2. trade-service 事件常量和接口

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/event/TradeMqTopics.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/event/ITradeEventPublisher.java
```

作用：

- 统一维护 topic/tag
- domain 通过接口发布事件
- infrastructure 实现具体 RocketMQ 投递

### 3. trade-service 可靠事件发布器

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRocketMqEventPublisher.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRocketMqProperties.java
```

核心逻辑：

```text
publish(topic, tag, key, payload)
  -> 生成 messageId = topic:tag:key
  -> 查询 trade_local_message
  -> 不存在则保存 INIT 消息
  -> 标记 PROCESSING
  -> MQ 关闭：mock publish，标记 SUCCESS
  -> MQ 开启：调用 DefaultMQProducer.send
  -> 发送成功：标记 SUCCESS
  -> 发送失败：恢复 INIT，增加 retry_count，设置 next_retry_time
```

这里复用了项目已有的 `trade_local_message` 表，不额外新增 trade 事件表。

### 4. trade-service 事件补偿任务

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/job/TradeRocketMqEventRetryJob.java
```

默认每 30 秒扫描一次：

```yaml
food:
  jobs:
    trade-event-retry:
      enabled: true
      fixed-delay-ms: 30000
      limit: 50
```

只重试：

```text
biz_type = TRADE_EVENT
message_status = INIT
next_retry_time <= now
```

不会影响原来普通订单库存同步、秒杀请求单这些本地消息。

### 5. 订单事件接入点

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

新增事件：

```text
普通下单成功       -> trade_order_topic:order.created
拼团锁单成功       -> trade_order_topic:order.created
秒杀同步下单成功   -> trade_order_topic:order.created
退款成功           -> trade_order_topic:order.refund.requested
退款成功           -> payment_topic:payment.refunded
核销成功           -> trade_order_topic:order.used
```

### 6. 秒杀异步下单事件

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/service/SeckillOrderService.java
```

当秒杀请求单被补偿任务真正处理为订单后：

```text
trade_order_topic:order.created
```

这样异步秒杀不会只停留在请求单层面。

### 7. 支付事件接入点

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/payment/service/PaymentOrderService.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/settlement/OrderPaySettlementService.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/payment/service/PaymentOrderTimeoutCloseService.java
```

新增事件：

```text
创建支付单       -> payment_topic:payment.created
支付回调成功     -> payment_topic:payment.success
订单结算成功     -> trade_order_topic:order.paid
支付单超时关闭   -> payment_topic:payment.closed
支付单超时取消订单 -> trade_order_topic:order.cancel.timeout
```

### 8. 普通订单超时事件

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/normal/service/NormalOrderTimeoutCancelService.java
```

普通待支付订单超时取消成功后：

```text
trade_order_topic:order.cancel.timeout
```

### 9. business-service 事件常量和接口

```text
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/event/BusinessMqTopics.java
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/event/IBusinessEventPublisher.java
```

作用和 trade-service 一样，domain 只依赖抽象。

### 10. 套餐库存事件

```text
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/packagee/service/PackageDomainService.java
```

新增事件：

```text
占用库存   -> package_stock_topic:stock.occupied
释放库存   -> package_stock_topic:stock.released
确认销量   -> package_stock_topic:stock.sold.confirmed
回滚销量   -> package_stock_topic:stock.rollback
```

### 11. 评价摘要异步更新

```text
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/review/service/ShopReviewDomainService.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/repository/ShopReviewRepository.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessReviewCreatedConsumer.java
```

改造前：

```text
创建评价
  -> 保存评价
  -> 同步更新 shop.comments / shop.score
```

改造后：

```text
创建评价
  -> 保存评价
  -> 发布 shop_review_topic:review.created
  -> consumer/fallback 更新 shop.comments / shop.score
  -> business_consumed_message 做消费幂等
```

### 12. business-service RocketMQ 发布器

```text
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRocketMqEventPublisher.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRocketMqProperties.java
```

MQ 关闭或发送失败时：

```text
review.created -> 本地 fallback applyReviewCreatedStats
其他事件 -> 只记录 mock publish 日志
```

这样保证本地联调时评分摘要仍然正确。

### 13. 消费幂等表

```text
docs/sql/food_business_db.sql
docs/sql/food_business_db_migration_063_business_consumed_message.sql
```

新增表：

```text
business_consumed_message
```

核心唯一键：

```text
uk_message_id(message_id)
```

用它解决 RocketMQ 至少一次投递下的重复消费问题。

## 配置变更

local：

```text
food-trade-service/food-trade-app/src/main/resources/application-local.yml
food-business-service/food-business-app/src/main/resources/application-local.yml
```

Nacos：

```text
deploy/nacos/configs/food-common.yaml
deploy/nacos/configs/food-trade-service.yaml
deploy/nacos/configs/food-business-service.yaml
```

默认：

```yaml
food:
  mq:
    enabled: ${FOOD_MQ_ENABLED:false}
    name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
```

## 本地数据库变更

已执行：

```powershell
Get-Content -Raw -Encoding UTF8 docs\sql\food_business_db_migration_063_business_consumed_message.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot
```

验证：

```sql
SHOW TABLES LIKE 'business_consumed_message';
SHOW INDEX FROM business_consumed_message;
```

结果：表和 `uk_message_id` 唯一索引均存在。

## 验证记录

全量测试：

```powershell
mvn test
```

结果：通过。

Nacos 配置发布与回读：

```powershell
scripts/publish-nacos-configs.ps1
scripts/verify-nacos-configs.ps1
```

结果：通过。

Nacos 模式重新构建启动：

```powershell
scripts/start-local-services-nacos.ps1 -Rebuild -Restart
```

结果：

```text
food-user-service healthy
food-business-service healthy
food-trade-service healthy
food-gateway-service healthy
```

Gateway 基础冒烟：

```powershell
scripts/smoke-gateway.ps1
```

结果：通过。

RocketMQ 事件专项冒烟：

```powershell
scripts/smoke-rocketmq-events.ps1 -Phone 13800138064
```

结果：

```text
登录成功
普通下单成功
支付单创建成功
支付回调成功
订单核销成功
评价创建成功
trade_local_message 中 TRADE_EVENT 成功消息 count=5
business_consumed_message 中 review.created 幂等消费记录 count=1
```

## 当前完成状态

```text
下单成功 -> 发送订单创建消息              已完成
支付成功 -> 发送支付成功消息              已完成
订单超时 -> 发送超时取消事件              已完成
库存变更 -> 发送库存事件                  已完成
库存扣减失败 -> 本地消息表补偿重试        已有能力，继续保留
评价创建 -> 异步/兜底更新店铺评分摘要     已完成
消息重复消费 -> 幂等表防重复              已完成
消息丢失 -> trade 本地消息表补偿          已完成
消息积压 -> retry limit 和补偿任务限量    已完成基础骨架
```

## 面试表达

```text
我在订单、支付、库存、评价这些状态变化点引入 RocketMQ 事件。
订单和支付事件不是直接裸发 MQ，而是先写本地消息表，发送成功后标记 SUCCESS，失败后通过补偿任务重试。
这样可以降低消息丢失风险，也能解释本地消息表的作用。
评价创建后不再强耦合更新店铺评分摘要，而是发布 review.created，消费者更新 shop 的 comments 和 score。
消费端用 business_consumed_message 的唯一键做幂等，解决 RocketMQ 至少一次投递带来的重复消费问题。
本地默认关闭真实 MQ，使用 mock-success/fallback 模式保证开发体验；生产只需要开启 food.mq.enabled 并配置 nameserver。
```


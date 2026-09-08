# 开发日志-082-business真实RabbitMQ发布收敛

## 本次目标

收敛 business-service 的 RabbitMQ 发布逻辑，移除“发送失败后本地 fallback 更新评价统计”的演示式逻辑，让 MQ 依赖真实生效。

## 背景问题

之前 `BusinessRabbitMqEventPublisher` 有两种不够真实的行为：

1. `food.mq.enabled=false` 时，打印 mock publish 日志。
2. RabbitMQ 发送失败时，直接调用 `shopReviewRepository.applyReviewCreatedStats(...)` 本地更新评价统计。

这会让项目看起来像接了 MQ，但在 MQ 不可用时仍然假装业务成功，不利于学习真实微服务链路，也不符合前面“所有插件真实跑通”的要求。

## 改造文件

文件：

- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRabbitMqEventPublisher.java`

## 改造后行为

### 1. MQ 未启用

直接抛出：

```text
business RabbitMQ publisher disabled
```

### 2. MQ 发送失败

直接抛出：

```text
business RabbitMQ publish failed
```

### 3. MQ 发送成功

正常发送 RabbitMQ message：

- exchange = topic
- routingKey = tag
- messageId = `topic:tag:key`
- body 包含 `eventId/topic/tag/key/payload/eventTime`

## 对业务链路的影响

以评价创建为例：

1. 用户创建评价。
2. business-service 保存评价。
3. business-service 发布 `review.created` 消息。
4. RabbitMQ consumer 消费消息。
5. consumer 执行 `applyReviewCreatedStats`。
6. 幂等表 `business_consumed_message` 防止重复消费。

改造后，评价统计只能通过 RabbitMQ consumer 更新，不再由 publisher 本地绕过。

## 为什么更接近公司项目

- MQ 依赖真实可见，RabbitMQ 没启动时不会假成功。
- 生产问题更早暴露。
- Publisher 和 Consumer 职责清晰。
- 后续要做消息可靠性时，可以继续补本地消息表或事务消息，而不是保留本地 fallback。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
```

结果：

- 后端全模块编译通过。

## 当前边界

本次移除了假成功 fallback，但还没有给 business-service 补本地消息表。

后续更完整的公司级做法：

- 创建评价时写 business outbox 本地消息表。
- 定时任务扫描未发送消息。
- RabbitMQ publisher confirm 更新发送状态。
- consumer 继续使用幂等表防重复消费。

# 数据记录-business本地消息表

## 表名

```text
food_business_db.business_local_message
```

## 解决的问题

评价创建属于 business-service 的本地业务，评价保存后还需要异步更新店铺评分摘要。

如果只在保存评价后直接发送 RabbitMQ，一旦 RabbitMQ 短暂不可用，就会出现：

```text
shop_review 已提交
review.created 消息丢失
shop 评分摘要没有更新
```

所以这里新增生产端本地消息表，保证业务数据和“待发布消息”在同一个 MySQL 本地事务里提交。

## 字段说明

| 字段 | 作用 |
| --- | --- |
| id | 自增主键 |
| message_id | 全局消息幂等 ID，当前格式是 `topic:tag:key` |
| message_type | 消息类型，对应 RabbitMQ routing key，例如 `review.created` |
| biz_type | 业务分类，当前固定为 `BUSINESS_EVENT` |
| biz_id | 业务 ID，评价事件里是 `reviewNo` |
| message_status | 消息状态：`INIT`、`PROCESSING`、`SUCCESS`、`FAILED` |
| retry_count | 当前重试次数 |
| max_retry_count | 最大重试次数，默认 5 |
| next_retry_time | 下一次可重试时间 |
| content | 消息完整 JSON 内容 |
| fail_reason | 最近一次投递失败原因 |
| create_time | 创建时间 |
| update_time | 更新时间 |

## 索引设计

| 索引 | 作用 |
| --- | --- |
| uk_message_id | 防止同一业务事件重复落本地消息 |
| idx_status_retry_time | 定时任务按状态和重试时间扫描 |
| idx_biz_type_id | 按业务维度追踪消息 |

## 当前业务链路

```text
ShopReviewDomainService.createReview
  -> @Transactional
  -> 保存 shop_review
  -> BusinessRabbitMqEventPublisher.publish
  -> 保存 business_local_message INIT
  -> 事务提交
  -> TransactionSynchronization.afterCommit
  -> 投递 RabbitMQ shop_review_topic / review.created
  -> 成功后 business_local_message 改 SUCCESS
  -> 失败后 business_local_message 改回 INIT 并设置 next_retry_time
```

## 定时重试

```text
BusinessRabbitMqEventRetryJob
  -> IBusinessEventPublisher.retryPendingEvents
  -> 扫描 INIT 且 next_retry_time 到期的消息
  -> 重新投递 RabbitMQ
```

配置位置：

- `food-business-service/food-business-app/src/main/resources/application-local.yml`
- `deploy/nacos/configs/food-business-service.yaml`

## 和 business_consumed_message 的区别

`business_local_message` 是生产端表，保证消息不丢。

`business_consumed_message` 是消费端幂等表，保证同一条消息不会重复更新店铺评分。

完整模型是：

```text
生产端本地消息表防丢
  + RabbitMQ 解耦异步
  + 消费端幂等表防重
```

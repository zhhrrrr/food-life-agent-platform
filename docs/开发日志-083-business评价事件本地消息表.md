# 开发日志-083-business评价事件本地消息表

## 本次目标

把 business-service 的评价创建事件从“直接投递 RabbitMQ”升级成“本地消息表 + 事务提交后投递 + 定时重试”。

## 为什么要改

之前评价创建链路是：

```text
保存 shop_review
  -> 直接发 RabbitMQ review.created
  -> consumer 更新店铺评分摘要
```

这个链路的问题是：如果评价已经保存成功，但 RabbitMQ 投递失败，店铺评分摘要就可能永远不更新。

## 改造后的链路

```text
ShopReviewDomainService.createReview
  -> 本地事务开始
  -> 校验订单可评价
  -> 保存 shop_review
  -> 保存 business_local_message INIT
  -> 本地事务提交
  -> afterCommit 尝试投递 RabbitMQ
  -> 投递成功：business_local_message 改 SUCCESS
  -> 投递失败：保留 INIT，记录 fail_reason 和 next_retry_time
  -> BusinessRabbitMqEventRetryJob 定时重试
  -> BusinessReviewCreatedListener 消费 review.created
  -> business_consumed_message 唯一索引防重复
  -> 更新 shop 评分摘要
```

## 新增文件

- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/dao/po/BusinessLocalMessagePO.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/dao/IBusinessLocalMessageMapper.java`
- `food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/job/BusinessRabbitMqEventRetryJob.java`
- `docs/sql/food_business_db_migration_083_business_local_message.sql`

## 修改文件

- `IBusinessEventPublisher`：新增 `retryPendingEvents(Integer limit)`
- `ShopReviewDomainService`：`createReview` 增加本地事务，评价和本地消息一起提交
- `BusinessRabbitMqEventPublisher`：改成本地消息表可靠投递
- `BusinessRabbitMqProperties`：新增重试间隔、扫描数量、PROCESSING 恢复超时配置
- `BusinessApplication`：增加 `@EnableScheduling`
- `application-local.yml` 和 `food-business-service.yaml`：新增 business 事件重试 Job 配置
- `food_business_db.sql`：补全 `business_local_message` 建表

## 和消费幂等表的区别

`business_local_message` 是生产端可靠投递表，解决“业务成功但消息没发出去”。

`business_consumed_message` 是消费端幂等表，解决“消息重复消费导致评分重复累加”。

这两个表职责不同，不能互相替代。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
npm run build
powershell -ExecutionPolicy Bypass -File scripts/verify-microservice-boundaries.ps1
```

结果：

- 后端全模块编译通过
- 前端构建通过
- 微服务边界验证通过
- 本地 MySQL 已创建 `food_business_db.business_local_message`

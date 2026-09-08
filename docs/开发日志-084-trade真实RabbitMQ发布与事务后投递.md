# 开发日志-084-trade真实RabbitMQ发布与事务后投递

## 本次目标

收敛 trade-service 的 RabbitMQ 可靠投递逻辑：

- 去掉 `food.mq.enabled=false` 时 mock publish 并标记成功的旧逻辑
- 增加事务提交后投递
- 增加 PROCESSING 卡住消息恢复
- 更新 local 与 Nacos 配置
- 更新微服务边界验证脚本

## 改造前问题

`TradeRabbitMqEventPublisher` 之前在 MQ 未启用时会：

```text
打印 mock publish 日志
trade_local_message 标记 SUCCESS
```

这会导致 `trade_local_message` 看起来成功，但 RabbitMQ 实际没有收到消息，不符合“真实依赖真实起作用”的要求。

## 改造后链路

```text
订单/支付/退款/核销领域逻辑
  -> tradeEventPublisher.publish / publishDelay
  -> 写入 trade_local_message INIT
  -> 如果当前存在事务：注册 TransactionSynchronization.afterCommit
  -> 事务提交后投递 RabbitMQ
  -> 投递成功：trade_local_message SUCCESS
  -> 投递失败或 MQ disabled：trade_local_message 回到 INIT，等待重试
  -> TradeRabbitMqEventRetryJob 定时扫描重试
```

## 为什么更像真实公司项目

- 不再用 mock success 掩盖基础设施故障
- 避免消息早于数据库事务提交导致消费端查不到业务数据
- PROCESSING 卡住后可以自动恢复，减少人工修复成本
- 仍保留本地消息表，满足最终一致性链路

## 修改文件

- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqEventPublisher.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqProperties.java`
- `food-trade-service/food-trade-app/src/main/resources/application-local.yml`
- `deploy/nacos/configs/food-trade-service.yaml`
- `scripts/verify-microservice-boundaries.ps1`

## 验证

已执行：

```bash
mvn -q -DskipTests compile
powershell -ExecutionPolicy Bypass -File scripts/verify-microservice-boundaries.ps1
```

结果：

- 后端全模块编译通过
- 微服务边界验证通过

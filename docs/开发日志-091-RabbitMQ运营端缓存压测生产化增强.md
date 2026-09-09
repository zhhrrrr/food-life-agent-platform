# 开发日志-091-RabbitMQ运营端缓存压测生产化增强

本次继续完成 Agent 之前的业务生产化补齐，范围覆盖 RabbitMQ 生产化增强、运营端页面、缓存治理、压测脚本，不进入 Agent 模块。

## 一、RabbitMQ 生产化增强

新增能力：

1. trade-service 队列增加 DLX/DLQ：
   - `food.trade.dlx`
   - `food.trade.order.event.dlq`
   - `food.trade.payment.event.dlq`
   - `food.trade.order.timeout.close.dlq`
2. business-service 队列增加 DLX/DLQ：
   - `food.business.dlx`
   - `food.business.review.created.dlq`
   - `food.business.package.stock.event.dlq`
3. Rabbit listener 设置 `default-requeue-rejected=false`，消费失败进入 DLQ。
4. 增加运营消息接口：
   - `GET /api/trade/operations/mq/messages`
   - `POST /api/trade/operations/mq/messages/{messageId}/republish`
   - `GET /api/trade/operations/mq/queues`
5. 增加消费耗时指标：
   - `food_mq_consume_duration_seconds`
6. 增加 RabbitMQ 项目队列拓扑重建脚本：
   - `scripts/reset-rabbitmq-food-topology.ps1`

关键文件：

- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqConfiguration.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRabbitMqConfiguration.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqEventPublisher.java`
- `food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OperationMqController.java`

## 二、运营端能力补齐

新增后端：

- 运营订单检索：`GET /api/trade/operations/orders`
- 业务缓存预热：`POST /api/business/operations/cache/preheat`

新增前端页面：

- 库存调整页：`/operations/stock`
- 退款确认页：`/operations/refunds`
- 异常消息处理页：`/operations/messages`
- 订单检索页：`/operations/orders`

这些页面未来可以作为 Agent 工具执行失败时的人工兜底后台。

## 三、缓存治理

business-service 新增 Redis 旁路缓存：

| 对象 | Key | TTL |
| --- | --- | --- |
| 店铺详情 | `food:cache:shop:{shopId}` | 10 分钟 |
| 套餐详情 | `food:cache:package:{packageId}` | 5 分钟 |
| 店铺套餐列表 | `food:cache:shop:{shopId}:packages` | 3 分钟 |
| 套餐交易快照 | `food:cache:package:{packageId}:trade-snapshot` | 2 分钟 |

更新后做立即删除 + 1 秒延迟删除，用于降低并发读写下旧缓存回填风险。

## 四、压测验证

新增 k6 脚本：

- `scripts/k6/normal-order.js`
- `scripts/k6/group-buy-order.js`
- `scripts/k6/seckill-order.js`
- `scripts/run-k6-food-flows.ps1`

覆盖普通下单、拼团锁单、秒杀异步请求单，便于观察 Gateway/Sentinel/Redis/RabbitMQ/DB 的链路表现。

## 五、可观测性补齐

Grafana 新增 MQ 消费 P95 面板：

- 指标：`food_mq_consume_duration_seconds_bucket`
- 看板：`deploy/observability/grafana/dashboards/food-life-agent-overview.json`

Prometheus 继续通过各服务 `/actuator/prometheus` 抓取。

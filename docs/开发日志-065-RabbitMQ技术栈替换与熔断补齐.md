# 开发日志-065-RabbitMQ技术栈替换与熔断补齐

## 本次目标

在第五阶段异步化方案里，将原 RocketMQ 技术栈替换为 RabbitMQ，并补齐第四阶段里还不够完整的 Sentinel 熔断能力。

本次不改变订单、支付、库存、评价的领域业务流程，只替换 infrastructure 层 MQ 实现，并增强远程调用治理。

## 一、RabbitMQ 替换范围

### 1. 依赖替换

根 pom 移除：

```xml
org.apache.rocketmq:rocketmq-client
```

trade/business infrastructure 改为：

```xml
org.springframework.boot:spring-boot-starter-amqp
```

trade infrastructure 额外引入 Sentinel starter，用于 Feign 出站调用手动埋点。

相关文件：

- `pom.xml`
- `food-trade-service/food-trade-infrastructure/pom.xml`
- `food-business-service/food-business-infrastructure/pom.xml`

### 2. trade-service MQ 实现

旧实现：

- `TradeRocketMqEventPublisher`
- `TradeRocketMqProperties`
- `TradeOrderTimeoutCloseConsumer`

新实现：

- `TradeRabbitMqEventPublisher`
- `TradeRabbitMqProperties`
- `TradeRabbitMqConfiguration`
- `TradeOrderTimeoutCloseListener`

职责对应关系：

- `TradeRabbitMqEventPublisher`：领域事件发布器，负责写本地消息表后的真实 RabbitMQ 投递
- `TradeRabbitMqConfiguration`：声明 exchange、queue、binding，并为订单/支付事件声明 durable event queue
- `TradeOrderTimeoutCloseListener`：消费延迟关单消息，到期后关闭未支付订单
- `TradeRabbitMqProperties`：承接 `food.mq.*` 配置

### 3. business-service MQ 实现

旧实现：

- `BusinessRocketMqEventPublisher`
- `BusinessRocketMqProperties`
- `BusinessReviewCreatedConsumer`

新实现：

- `BusinessRabbitMqEventPublisher`
- `BusinessRabbitMqProperties`
- `BusinessRabbitMqConfiguration`
- `BusinessReviewCreatedListener`

职责对应关系：

- `BusinessRabbitMqEventPublisher`：库存事件、评价事件发布
- `BusinessRabbitMqConfiguration`：声明 package stock / shop review exchange，以及 stock/review 事件队列
- `BusinessReviewCreatedListener`：消费评价创建事件，异步更新店铺评分摘要

## 二、RabbitMQ 业务流

### 1. 下单成功事件

```text
trade-service 下单成功
  -> 领域层调用 ITradeEventPublisher.publish
  -> TradeRabbitMqEventPublisher 写入/读取 trade_local_message
  -> food.mq.enabled=false：本地 mock-success
  -> food.mq.enabled=true：发送到 RabbitMQ exchange
```

事件仍使用领域层通用命名：

- exchange：`trade_order_topic`
- routing key：`order.created`

### 2. 支付成功事件

```text
支付成功回调
  -> PaymentOrderService 完成支付单和订单状态更新
  -> 发布 payment.success
  -> OrderPaySettlementService 发布 order.paid
```

对应 RabbitMQ：

- exchange：`payment_topic`
- routing key：`payment.success`
- exchange：`trade_order_topic`
- routing key：`order.paid`

### 3. 订单延迟关单

RabbitMQ 没有 RocketMQ 4.x 的内置 delayLevel，本次采用 TTL + DLX：

```text
下单成功
  -> publishDelay(order.cancel.timeout)
  -> 发送到 trade_order_topic / order.cancel.timeout.delay
  -> 进入 food.trade.order.timeout.delay.queue
  -> TTL 到期
  -> 死信转发到 trade_order_topic / order.cancel.timeout
  -> food.trade.order.timeout.close.queue 收到消息
  -> TradeOrderTimeoutCloseListener 消费
  -> OrderTimeoutDelayCloseService 关闭未支付订单
```

默认延迟时间：

```yaml
food:
  mq:
    order-timeout-delay-millis: 1800000
```

也就是 30 分钟。

### 4. 评价创建异步更新评分

```text
用户核销后创建评价
  -> business-service 发布 review.created
  -> food.mq.enabled=false：本地 fallback 直接 applyReviewCreatedStats
  -> food.mq.enabled=true：发送到 shop_review_topic / review.created
  -> food.business.review.created.queue 消费
  -> BusinessReviewCreatedListener 调用 applyReviewCreatedStats
  -> business_consumed_message 做消费幂等
```

## 三、配置变化

公共 Nacos 配置新增：

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

trade-service 新增：

```yaml
food:
  mq:
    order-timeout-close-queue: food.trade.order.timeout.close.queue
    order-timeout-delay-queue: food.trade.order.timeout.delay.queue
    trade-order-event-queue: food.trade.order.event.queue
    payment-event-queue: food.trade.payment.event.queue
    order-timeout-delay-routing-key: order.cancel.timeout.delay
    order-timeout-delay-millis: ${ORDER_TIMEOUT_DELAY_MILLIS:1800000}
    retry-delay-seconds: 30
    retry-limit: 50
```

business-service 新增：

```yaml
food:
  mq:
    review-created-queue: food.business.review.created.queue
    package-stock-event-queue: food.business.package.stock.event.queue
```

本地 `application-local.yml` 同步保留这些配置，Nacos 不可用时仍可运行。

## 四、熔断补齐

### 1. trade-service 入口熔断规则

在 `TradeSentinelRuleConfiguration` 中新增 DegradeRule：

- 普通下单：`trade.order.normal.create`
- 拼团下单：`trade.order.groupBuy.create`
- 秒杀同步下单：`trade.order.seckill.create`
- 秒杀异步请求：`trade.order.seckill.asyncCreate`
- 支付回调：`trade.payment.callback`

规则类型：

```text
异常比例熔断
统计窗口 10 秒
熔断窗口 10 秒
最小请求数 10
```

### 2. Feign 出站熔断

在 `BusinessPackagePort` 中新增手动 Sentinel entry：

- `trade.feign.business.package.snapshot`
- `trade.feign.business.package.stock`

业务效果：

```text
trade-service 调 business-service
  -> 进入 Sentinel 出站资源
  -> Feign 异常 / fallback 503 记入异常统计
  -> 达到异常比例阈值
  -> Sentinel 打开熔断
  -> 后续请求快速失败
  -> Controller 统一返回 503
```

这比只写 Feign fallback 更完整，因为 fallback 只处理“失败以后怎么返回”，熔断还负责“失败太多时主动短路保护”。

### 3. business-service 库存熔断

在 `BusinessSentinelRuleConfiguration` 中新增库存接口 DegradeRule：

- `business.package.stock.occupy`
- `business.package.stock.release`
- `business.package.sold.confirm`
- `business.package.sold.rollback`

规则类型：

```text
异常比例熔断
统计窗口 10 秒
熔断窗口 10 秒
最小请求数 5
```

## 五、当前仍保持的设计

### 1. 领域层不依赖 RabbitMQ

领域层仍只依赖：

- `ITradeEventPublisher`
- `IBusinessEventPublisher`

RabbitMQ 只在 infrastructure 层出现，符合 DDD 的依赖方向。

### 2. 本地默认不强依赖 RabbitMQ

默认：

```yaml
food:
  mq:
    enabled: false
```

所以本地开发不启动 RabbitMQ 也能跑通主流程。

打开真实 RabbitMQ：

```powershell
$env:FOOD_MQ_ENABLED="true"
$env:RABBITMQ_HOST="127.0.0.1"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
```

## 六、本次新增/修改文件

新增：

- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqConfiguration.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqEventPublisher.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeRabbitMqProperties.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/mq/TradeOrderTimeoutCloseListener.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRabbitMqConfiguration.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRabbitMqEventPublisher.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessRabbitMqProperties.java`
- `food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/mq/BusinessReviewCreatedListener.java`
- `food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/job/TradeRabbitMqEventRetryJob.java`
- `scripts/smoke-rabbitmq-events.ps1`
- `docs/数据记录-RabbitMQ消息治理.md`

删除：

- `TradeRocketMqEventPublisher`
- `TradeRocketMqProperties`
- `TradeOrderTimeoutCloseConsumer`
- `BusinessRocketMqEventPublisher`
- `BusinessRocketMqProperties`
- `BusinessReviewCreatedConsumer`
- `TradeRocketMqEventRetryJob`
- `scripts/smoke-rocketmq-events.ps1`

修改：

- `pom.xml`
- `deploy/nacos/configs/food-common.yaml`
- `deploy/nacos/configs/food-trade-service.yaml`
- `deploy/nacos/configs/food-business-service.yaml`
- `food-trade-service/food-trade-app/src/main/resources/application-local.yml`
- `food-business-service/food-business-app/src/main/resources/application-local.yml`
- `BusinessPackagePort`
- `TradeSentinelRuleConfiguration`
- `BusinessSentinelRuleConfiguration`
- `TradeSentinelResources`
- `OrderTimeoutDelayCloseService`
- `docs/sql/food_business_db.sql`
- `docs/sql/food_business_db_migration_063_business_consumed_message.sql`

## 七、面试可讲点

### 为什么换 RabbitMQ

RabbitMQ 更适合讲业务事件异步化、延迟队列、死信队列、消费幂等、削峰和最终一致性。

本项目里，RabbitMQ 用在：

- 下单后事件发布
- 支付成功后事件发布
- 超时关单
- 评价创建后异步刷新店铺评分

### RabbitMQ 怎么做延迟消息

本次使用 TTL + DLX，不依赖 delayed-message 插件。

优点：

- RabbitMQ 标准能力
- 本地和生产都容易部署
- 面试解释成本低

注意：

- 队列级 TTL 对不同延迟不灵活
- 本次用消息级 expiration，适合订单 30 分钟固定延迟场景

### 熔断和降级区别

降级：

```text
调用失败后返回兜底响应
```

熔断：

```text
失败比例过高后主动短路，避免继续打爆下游
```

本次做法：

- Feign fallback 负责兜底返回
- Sentinel DegradeRule 负责熔断
- `BusinessPackagePort` 手动 entry 负责把远程调用纳入 Sentinel 统计
## 八、验证结果

已执行：

```powershell
mvn test
```

结果：25 个 Maven 模块全部 SUCCESS。

已启动：

```powershell
.\scripts\start-nacos-server.ps1
.\scripts\publish-nacos-configs.ps1
$env:FOOD_MQ_ENABLED="false"
.\scripts\start-local-services-nacos.ps1 -Rebuild -Restart
```

结果：

- `food-user-service` 8101 health 正常
- `food-business-service` 8201 health 正常
- `food-trade-service` 8301 health 正常
- `food-gateway-service` 8080 health 正常

已执行：

```powershell
.\scripts\verify-nacos-services.ps1
.\scripts\smoke-gateway.ps1
.\scripts\smoke-rabbitmq-events.ps1 -Phone 13800138065
```

结果：

- Nacos 四个服务均有健康实例
- Gateway health、黑名单、未登录鉴权、店铺/套餐路由全部通过
- 普通下单、支付准备、支付回调、核销、评价创建全部通过
- `trade_local_message` 成功写入订单/支付/延迟关单事件
- `business_consumed_message` 成功写入评价消费幂等记录

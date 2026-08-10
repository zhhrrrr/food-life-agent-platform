# 开发日志 028 - trade 服务秒杀请求单与本地消息表

## 一、本次目标

上一阶段秒杀已经升级为 Redis + Lua 预占库存，但接口线程仍然同步创建 MySQL 订单。

本次新增异步削峰骨架：

```text
Redis Lua 抢资格
-> 写入秒杀请求单
-> 写入本地消息表
-> 后台任务异步创建订单
-> 请求单绑定真实 orderId
```

当前不引入 MQ，先用本地消息表模拟可靠消息，保持服务数量少。

## 二、新增接口

### 1. 异步秒杀下单

```http
POST /api/trade/orders/seckill/async
```

返回 `requestNo`，不直接返回 `orderId`。

### 2. 查询秒杀请求单

```http
GET /api/trade/seckill/order-requests/{requestNo}
```

用于查询异步落单结果。

### 3. 手动触发消息消费

```http
POST /api/trade/seckill/order-requests/process?limit=10
```

方便本地验证。生产里可以由定时任务自动处理，后续再替换为 MQ 消费。

## 三、新增数据表

### 1. `seckill_order_request`

职责：

```text
记录用户抢秒杀资格后的请求单。
请求单是 Redis 资格预占成功后的业务凭据。
```

关键字段：

```text
request_no
user_id
activity_id
package_id
quantity
order_id
order_no
request_status
fail_reason
```

状态：

```text
INIT
PROCESSING
SUCCESS
FAILED
```

### 2. `trade_local_message`

职责：

```text
模拟可靠消息。
秒杀请求单创建成功后，同事务写入本地消息。
后台任务扫描 INIT 消息并异步创建真实订单。
```

关键字段：

```text
message_id
message_type
biz_type
biz_id
message_status
retry_count
max_retry_count
next_retry_time
content
fail_reason
```

状态：

```text
INIT
PROCESSING
SUCCESS
FAILED
```

## 四、本次新增文件

领域模型：

```text
SeckillOrderRequestEntity
SeckillOrderRequestResult
SeckillOrderRequestProcessResult
TradeLocalMessageEntity
```

领域常量：

```text
SeckillRequestStatusConstants
LocalMessageStatusConstants
```

基础设施：

```text
SeckillOrderRequestPO
TradeLocalMessagePO
ISeckillOrderRequestMapper
ITradeLocalMessageMapper
```

触发层：

```text
SeckillOrderRequestProcessJob
```

API DTO：

```text
CreateSeckillOrderRequestResponseDTO
SeckillOrderRequestQueryResponseDTO
SeckillOrderRequestProcessResponseDTO
```

## 五、业务流程

### 1. 异步抢资格

```text
1. 校验用户登录。
2. 校验 activityId、quantity。
3. 查询并校验 MySQL 秒杀活动。
4. 查询 MySQL 已购记录兜底防重复。
5. Redis Lua 原子预占库存和用户资格。
6. 生成 requestNo。
7. 同事务写入 seckill_order_request 和 trade_local_message。
8. 接口返回 requestNo、INIT、remainingStock。
```

### 2. 异步落单

```text
1. 后台任务扫描 trade_local_message 中 INIT 且到达 next_retry_time 的消息。
2. 抢占消息状态 INIT -> PROCESSING。
3. 查询 seckill_order_request。
4. 请求单状态 INIT/FAILED -> PROCESSING。
5. 创建 dining_order、dining_order_item、seckill_order。
6. 更新 seckill_order_request 为 SUCCESS 并绑定 orderId/orderNo。
7. 更新 trade_local_message 为 SUCCESS。
```

### 3. 异常补偿

```text
1. 创建订单失败时，消息 retry_count + 1。
2. 未达到最大重试次数时，消息回到 INIT，等待下一轮扫描。
3. 达到最大重试次数后，请求单和消息标记 FAILED。
4. 最终失败时释放 Redis 库存和用户占位。
```

## 六、本地验证

编译：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
```

结果：`BUILD SUCCESS`

打包：

```bash
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
```

结果：`BUILD SUCCESS`

测试：

```bash
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`

本地 SQL：

```bash
mysql -uroot -proot -e "source F:/4F/FinalProject/美食生活业务Agent项目/food-life-agent-platform/docs/sql/food_trade_db.sql"
```

结果：成功。

### 接口验证数据

登录用户：

```text
phone = 13600136061
userId = 33
```

预热活动：

```json
{
  "activityId": 1,
  "dbStock": 18,
  "redisStock": 18
}
```

异步下单：

```json
{
  "requestNo": "SK178635256105433",
  "activityId": 1,
  "packageId": 1,
  "quantity": 1,
  "requestStatus": "INIT",
  "remainingStock": 17
}
```

请求单初始查询：

```json
{
  "requestNo": "SK178635256105433",
  "userId": 33,
  "activityId": 1,
  "packageId": 1,
  "quantity": 1,
  "orderId": null,
  "orderNo": null,
  "requestStatus": "INIT",
  "failReason": null
}
```

手动触发消费：

```json
{
  "scannedCount": 1,
  "successCount": 1,
  "failedCount": 0,
  "retryCount": 0
}
```

请求单最终查询：

```json
{
  "requestNo": "SK178635256105433",
  "userId": 33,
  "activityId": 1,
  "packageId": 1,
  "quantity": 1,
  "orderId": 40,
  "orderNo": "NO178635256122233",
  "requestStatus": "SUCCESS",
  "failReason": null
}
```

MySQL 验证：

```text
seckill_order_request.request_status = SUCCESS
trade_local_message.message_status = SUCCESS
dining_order.id = 40
dining_order.trade_type = SECKILL
dining_order.order_status = WAIT_PAY
seckill_order.order_id = 40
seckill_activity.stock = 17
```

Redis 验证：

```text
food:trade:seckill:stock:1 从 18 变为 17
```

## 七、当前边界

当前仍然不是完整 MQ 架构：

```text
本地消息表 + 定时任务
```

后续可替换为：

```text
RocketMQ/RabbitMQ/Kafka 消息投递
消费者幂等表
死信队列
库存对账任务
请求单超时取消任务
```

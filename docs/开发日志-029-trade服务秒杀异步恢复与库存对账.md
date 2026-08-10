# 开发日志 029 - trade 服务秒杀异步恢复与库存对账

## 一、本次目标

上一阶段已经完成：

```text
Redis Lua 抢资格
-> 秒杀请求单
-> 本地消息表
-> 异步落单
```

本次继续补生产化异常恢复：

```text
1. PROCESSING 消息卡死恢复
2. 超时请求单取消
3. Redis 秒杀库存对账
```

## 二、新增接口

### 1. 恢复异步请求链路

```http
POST /api/trade/seckill/order-requests/recover?limit=10
```

职责：

```text
恢复卡死的 PROCESSING 消息。
取消长时间未成功落单的请求单。
释放超时请求占用的 Redis 库存和用户占位。
```

### 2. 秒杀库存对账

```http
POST /api/trade/seckill/activities/{activityId}/stock/reconcile
```

职责：

```text
以 MySQL seckill_activity.stock 为准刷新 Redis 库存。
同时返回 WAIT_PAY、PAID 秒杀订单数量，辅助观察库存状态。
```

## 三、新增文件

```text
SeckillOrderRequestRecoveryResult
SeckillStockReconcileResult
SeckillOrderRequestRecoveryResponseDTO
SeckillStockReconcileResponseDTO
SeckillOrderRequestRecoveryJob
```

## 四、核心逻辑

### 1. PROCESSING 消息恢复

扫描条件：

```text
trade_local_message.message_type = SECKILL_ORDER_CREATE
trade_local_message.message_status = PROCESSING
trade_local_message.update_time <= now - 120s
```

恢复动作：

```text
message_status: PROCESSING -> INIT
retry_count: retry_count + 1
next_retry_time: now
fail_reason: recover stuck processing message
```

### 2. 请求单超时取消

扫描条件：

```text
seckill_order_request.request_status in (INIT, PROCESSING)
seckill_order_request.order_id is null
seckill_order_request.create_time <= now - 300s
```

取消动作：

```text
request_status -> FAILED
fail_reason -> seckill order request timeout
释放 Redis 库存
释放 Redis 用户占位
```

### 3. 库存对账

对账逻辑：

```text
查询 MySQL seckill_activity.stock
查询 Redis food:trade:seckill:stock:{activityId}
用 MySQL stock 刷新 Redis stock
返回刷新前后库存
```

## 五、本地验证

### 1. 编译

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
```

结果：`BUILD SUCCESS`

### 2. 打包

```bash
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
```

结果：`BUILD SUCCESS`

### 3. 测试

```bash
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`

### 4. 库存对账验证

先预热：

```json
{
  "activityId": 1,
  "dbStock": 17,
  "redisStock": 17
}
```

手工把 Redis 库存改成：

```text
food:trade:seckill:stock:1 = 999
```

调用对账：

```json
{
  "activityId": 1,
  "dbStock": 17,
  "redisStockBefore": 999,
  "redisStockAfter": 17,
  "waitPayCount": 1,
  "paidCount": 2,
  "refreshed": true
}
```

### 5. 恢复接口验证

构造一条卡死消息：

```text
message_id = MSG_RECOVERY_STUCK_2
message_status = PROCESSING
update_time = now - 10 minute
```

构造一条超时请求：

```text
request_no = SK_RECOVERY_TIMEOUT_2
user_id = 35
request_status = INIT
create_time = now - 10 minute
```

同时模拟 Redis 已预占：

```text
Redis stock: 17 -> 16
food:trade:seckill:user:1[35] = 1
```

调用恢复接口：

```json
{
  "scannedMessageCount": 1,
  "recoveredMessageCount": 1,
  "canceledRequestCount": 1,
  "releasedStockCount": 1
}
```

恢复后：

```text
Redis stock: 16 -> 17
seckill_order_request.request_status = FAILED
trade_local_message.message_status = INIT
trade_local_message.retry_count = 1
```

## 六、当前边界

当前恢复逻辑仍是本地定时任务：

```text
SeckillOrderRequestRecoveryJob
```

配置：

```yaml
food:
  trade:
    seckill:
      order-request-recovery:
        enabled: true
        fixed-delay-ms: 60000
```

后续如果接 MQ，需要继续补：

```text
死信队列
消息重投
消费幂等表
库存周期对账任务
```

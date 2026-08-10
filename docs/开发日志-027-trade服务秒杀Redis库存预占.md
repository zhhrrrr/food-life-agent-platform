# 开发日志 027 - trade 服务秒杀 Redis 库存预占

## 一、本次目标

上一阶段已经完成秒杀下单骨架，但库存扣减直接落在 MySQL 上。

本次把秒杀链路升级为更接近生产的版本：

```text
MySQL 保存最终交易数据
Redis 承接秒杀高并发库存预占
Lua 保证库存扣减和用户限购判断的原子性
```

当前仍不引入 MQ，订单落库继续走 Java 侧同步流程。这样服务数量不增加，复杂度也还能被项目当前阶段接住。

## 二、新增接口

```http
POST /api/trade/seckill/activities/{activityId}/stock/preheat
GET /api/trade/seckill/activities?packageId=1
POST /api/trade/orders/seckill
```

预热接口用于模拟生产环境中活动发布前，把 MySQL 活动库存加载到 Redis。

活动查询如果发现 Redis 中已经存在活动库存，则响应里的 `stock` 优先展示 Redis 库存。

秒杀下单前先经过 Redis Lua 脚本做资格预占，预占成功后才继续创建 MySQL 订单。

## 三、本次新增文件

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/model/SeckillStockOccupyResult.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/model/SeckillStockPreheatResult.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/repository/ISeckillStockRepository.java
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/SeckillStockPreheatResponseDTO.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/SeckillStockRedisRepository.java
food-trade-service/food-trade-infrastructure/src/main/resources/lua/seckill_stock_occupy.lua
food-trade-service/food-trade-infrastructure/src/main/resources/lua/seckill_stock_release.lua
```

## 四、核心业务流程

活动预热：

```text
1. 查询 MySQL seckill_activity。
2. 写入 Redis 活动 Hash。
3. 写入 Redis 库存 String。
4. 设置活动、库存、用户占位 Key 的 TTL。
```

秒杀下单：

```text
1. 校验用户登录。
2. 校验 activityId、quantity。
3. 查询 MySQL 活动，校验状态、时间窗、DB 库存。
4. 查询 MySQL 用户已抢购记录，兜底防重复。
5. 调用 Redis Lua 做资格预占。
6. 如果 Redis 未预热，自动预热一次后重试。
7. Redis 预占成功后，查询 business-service 套餐交易快照。
8. 构建 DiningOrder、DiningOrderItem、SeckillOrder。
9. 保存 MySQL 订单，同时 DB 库存仍做原子扣减兜底。
10. 保存失败时释放 Redis 库存和用户占位。
```

未支付取消：

```text
1. MySQL dining_order 从 WAIT_PAY 改为 CANCELED。
2. MySQL seckill_order 从 WAIT_PAY 改为 CANCELED。
3. MySQL seckill_activity.stock + 1。
4. Redis seckill stock + 1。
5. Redis 用户占位释放。
```

## 五、Redis Key 设计

活动信息：

```text
food:trade:seckill:activity:{activityId}
```

活动库存：

```text
food:trade:seckill:stock:{activityId}
```

用户占位：

```text
food:trade:seckill:user:{activityId}
```

用户占位使用 Hash：

```text
field = userId
value = 当前用户在该活动中的预占次数
```

## 六、本地验证记录

编译：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
```

结果：`BUILD SUCCESS`

打包：

```bash
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
```

第一次因为旧 jar 正在运行，Windows 占用文件导致失败；停止 8301 旧进程后重新打包成功。

单元测试：

```bash
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`

接口验证：

```text
user-service     8101
business-service 8201
trade-service    8301
redis            6379
```

登录用户：

```text
phone = 13600136052
userId = 32
```

预热活动 1：

```json
{
  "activityId": 1,
  "dbStock": 18,
  "redisStock": 18,
  "stockKey": "food:trade:seckill:stock:1",
  "userKey": "food:trade:seckill:user:1"
}
```

秒杀下单：

```json
{
  "activityId": 1,
  "packageId": 1,
  "orderId": 39,
  "orderNo": "NO178635070149832",
  "payAmount": 9800,
  "orderStatus": "WAIT_PAY",
  "remainingStock": 17
}
```

重复下单：

```json
{
  "code": "400",
  "message": "seckill user take limit",
  "data": null
}
```

取消后验证：

```text
Redis 库存恢复为 18。
Redis 用户占位清除。
MySQL seckill_activity.stock = 18。
dining_order.order_status = CANCELED。
seckill_order.order_status = CANCELED。
```

## 七、当前边界

当前仍然是同步下单：

```text
Redis 预占成功后，Java 线程继续同步创建 MySQL 订单。
```

还没有引入：

```text
MQ 异步落单
本地消息表
库存对账任务
Redis 预热后台任务
真实支付回调
```

后续建议继续做：

```text
秒杀请求单 + 本地消息表 + 异步落单补偿。
```

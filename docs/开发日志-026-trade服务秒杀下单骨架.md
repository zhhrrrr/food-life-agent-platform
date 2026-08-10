# 开发日志 026 - trade 服务秒杀下单骨架

## 本次目标

完成秒杀业务第一版可运行骨架：

- 秒杀活动表
- 秒杀订单关系表
- 活动列表查询
- 秒杀下单
- MySQL 原子扣库存
- 用户限购
- 支付同步
- 未支付取消回滚库存

本阶段先不引入 Redis/MQ，先把 DDD 分层和主业务闭环跑通。

## 新增接口

### 秒杀活动列表

```http
GET /api/trade/seckill/activities?packageId=1
```

### 秒杀下单

```http
POST /api/trade/orders/seckill
```

```json
{
  "activityId": 1,
  "quantity": 1
}
```

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `TradeTypeConstants` | 新增 `SECKILL` 交易类型 |
| `SeckillActivityEntity` | 秒杀活动领域实体 |
| `SeckillActivityView` | 秒杀活动查询读模型 |
| `SeckillOrderCommand` | 秒杀下单命令 |
| `SeckillOrderAggregate` | 秒杀下单聚合 |
| `SeckillOrderEntity` | 秒杀订单关系实体 |
| `SeckillOrderResult` | 秒杀下单结果 |
| `ISeckillRepository` | 秒杀仓储接口 |
| `SeckillOrderService` | 秒杀下单领域服务 |
| `SeckillActivityPO` / `SeckillOrderPO` | 秒杀 MyBatis-Plus PO |
| `ISeckillActivityMapper` / `ISeckillOrderMapper` | 秒杀 Mapper |
| `SeckillRepository` | 秒杀仓储实现，负责事务、扣库存、状态同步 |
| `OrderDomainService` | 接入秒杀下单和秒杀取消 |
| `OrderPaySettlementService` | 接入秒杀支付成功同步 |
| `OrderController` | 新增秒杀活动查询和秒杀下单 HTTP 入口 |
| `docs/sql/food_trade_db.sql` | 新增秒杀表和本地初始化数据 |
| `docs/数据记录-秒杀数据.md` | 新增秒杀数据记录 |

## 业务流程

### 秒杀活动查询

1. 查询启用活动。
2. 过滤未结束、库存大于 0 的活动。
3. 如果传 `packageId`，只查对应套餐。
4. 返回 `canBuy`。

### 秒杀下单

1. 校验用户登录。
2. 校验 `activityId`、`quantity`。
3. 查询秒杀活动。
4. 校验活动启用、时间窗、库存。
5. 校验用户限购。
6. 查询套餐交易快照。
7. 构建主订单、订单明细、秒杀订单关系。
8. 事务内原子扣库存。
9. 写入 `dining_order`。
10. 写入 `dining_order_item`。
11. 写入 `seckill_order`。

### 秒杀支付

复用模拟支付入口：

```http
POST /api/trade/orders/{orderId}/pay/mock
```

秒杀支付成功后：

- `dining_order.order_status = PAID`
- `seckill_order.order_status = PAID`

### 秒杀未支付取消

复用取消入口：

```http
POST /api/trade/orders/{orderId}/cancel
```

秒杀未支付取消后：

- `dining_order.order_status = CANCELED`
- `seckill_order.order_status = CANCELED`
- `seckill_activity.stock = stock + 1`

## 本地验证

已执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`。

### 活动查询

```json
{
  "activityId": 1,
  "packageId": 1,
  "activityName": "local seckill meal package",
  "seckillPrice": 9800,
  "stock": 20,
  "userTakeLimit": 1,
  "canBuy": true
}
```

### A 用户秒杀下单

库存 `20 -> 19`。

```json
{
  "activityId": 1,
  "packageId": 1,
  "orderId": 35,
  "payAmount": 9800,
  "orderStatus": "WAIT_PAY",
  "remainingStock": 19
}
```

### A 用户重复秒杀

```json
{
  "code": "400",
  "message": "seckill user take limit",
  "data": null
}
```

### A 用户支付

```text
dining_order: 35 SECKILL PAID 9800
seckill_order: 35 1 PAID
```

补充验证订单 `37`：支付响应已返回 `activityId = 1`，便于 Agent 追踪秒杀业务来源。

### B 用户秒杀后取消

下单后库存 `19 -> 18`，取消后库存 `18 -> 19`。

```text
dining_order: 36 SECKILL CANCELED 9800
seckill_order: 36 1 CANCELED
```

## 当前边界

- 当前使用 MySQL 原子扣库存，适合第一阶段本地验证。
- 高并发秒杀下一步应引入 Redis 活动库存预热、Lua 原子扣减、MQ 异步创建订单。
- 当前秒杀退款未单独扩展，后续可以参考拼团退款策略补齐。

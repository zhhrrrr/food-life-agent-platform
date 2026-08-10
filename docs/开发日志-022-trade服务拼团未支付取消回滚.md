# 开发日志 022 - trade 服务拼团未支付取消回滚

## 本次目标

在拼团锁单和拼团支付结算之后，补齐第一段反向流程：拼团订单未支付时取消，需要回滚拼团锁单数据。

仍然复用现有取消入口：

```http
POST /api/trade/orders/{orderId}/cancel
```

## 参考 xfg 拼团逻辑

| xfg 拼团逻辑 | 当前项目迁移结果 |
| --- | --- |
| `Unpaid2RefundStrategy` | 当前项目实现为 `cancelUnpaidGroupBuyOrder` |
| `group_buy_order_list.status = 2` | `group_buy_order_list.order_status = CANCELED` |
| `group_buy_order.lock_count + (-1)` | `group_buy_order.lock_count = lock_count - 1` |
| Redis 恢复锁单库存 | 当前项目先恢复 DB 活动库存 `group_buy_activity.stock + 1` |
| MQ/NotifyTask 异步通知 | 当前阶段暂不实现，后续接消息任务骨架 |

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `OrderDomainService` | 取消订单时识别 `GROUP_BUY`，走拼团未支付取消回滚 |
| `IGroupBuyRepository` | 新增 `cancelUnpaidGroupBuyOrder` |
| `GroupBuyRepository` | 新增取消回滚事务；同时修正用户参与次数只统计 `LOCKED/PAID` |
| `GroupBuyStatusConstants` | 新增 `CANCELED` |
| `docs/sql/food_trade_db.sql` | 补充拼团明细状态说明 |
| `docs/数据记录-拼团锁单数据.md` | 补充拼团未支付取消写入关系 |

## 业务流程

1. 用户调用 `/api/trade/orders/{orderId}/cancel`。
2. 领域服务校验：
   - 用户已登录；
   - 订单存在；
   - 订单属于当前用户；
   - 订单状态为 `WAIT_PAY`。
3. 判断订单类型：
   - `NORMAL`：沿用原逻辑，只取消主订单；
   - `GROUP_BUY`：进入拼团取消回滚事务。
4. 拼团取消回滚事务：
   - `dining_order.order_status = CANCELED`；
   - `group_buy_order_list.order_status = CANCELED`；
   - `group_buy_order.lock_count = lock_count - 1`；
   - `group_buy_activity.stock = stock + 1`。
5. 返回订单取消结果。

## 当前边界

本次只处理未支付取消，也就是 `GROUP_BUY + WAIT_PAY`。已经支付但未成团退款、已经成团退款，会在后续退款策略中继续按 xfg 拼团的 `Paid2RefundStrategy`、`PaidTeam2RefundStrategy` 迁移。

## 本地验证

### 编译和打包

执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：均为 `BUILD SUCCESS`。

### 接口验证

验证 1：用户锁定拼团订单后取消。

锁单返回：

```json
{
  "orderId": 24,
  "teamId": "GBT4C5C08E5F087481E",
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "lockCount": 1,
  "completeCount": 0
}
```

取消返回：

```json
{
  "orderId": 24,
  "orderNo": "NO178633126319318",
  "orderStatus": "CANCELED"
}
```

验证 2：同一用户取消后再次参加。

再次锁单成功，说明 `user_take_limit` 不再被已取消记录占用：

```json
{
  "orderId": 25,
  "teamId": "GBT4AD1916BD4E34217",
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "lockCount": 1,
  "completeCount": 0
}
```

随后取消验证订单 25，避免验证数据继续占用库存。

### 落库结果

```text
dining_order:
24 GROUP_BUY CANCELED
25 GROUP_BUY CANCELED

group_buy_order:
GBT4C5C08E5F087481E lock_count=0 complete_count=0 team_status=IN_PROGRESS
GBT4AD1916BD4E34217 lock_count=0 complete_count=0 team_status=IN_PROGRESS

group_buy_order_list:
order_id=24 order_status=CANCELED
order_id=25 order_status=CANCELED

group_buy_activity:
id=1 stock=96
```

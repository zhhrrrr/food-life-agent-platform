# 开发日志 023 - trade 服务拼团退款策略

## 本次目标

一次性补齐拼团已支付退款主流程，并按 xfg 拼团的策略模式拆分退款场景。

复用现有入口：

```http
POST /api/trade/orders/{orderId}/refund/mock
```

## 参考 xfg 拼团逻辑

| xfg 拼团 | 当前项目 |
| --- | --- |
| `IRefundOrderStrategy` | `IGroupBuyRefundStrategy` |
| `Paid2RefundStrategy` | `PaidUnformedGroupBuyRefundStrategy` |
| `PaidTeam2RefundStrategy` | `PaidFormedGroupBuyRefundStrategy` |
| `RefundTypeEnumVO` 路由策略 | `GroupBuyRefundStrategyRouter` 根据队伍状态路由 |
| `group_buy_order_list.status = 2` | `group_buy_order_list.order_status = REFUNDED` |
| `group_buy_order.lock_count + (-1)` | `group_buy_order.lock_count = lock_count - 1` |
| `group_buy_order.complete_count + (-1)` | `group_buy_order.complete_count = complete_count - 1` |
| `COMPLETE_FAIL / FAIL` | `COMPLETE_FAIL / FAILED` |

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `IGroupBuyRefundStrategy` | 拼团退款策略接口 |
| `AbstractGroupBuyRefundStrategy` | 拼团退款策略公共响应构建 |
| `PaidUnformedGroupBuyRefundStrategy` | 已支付、未成团退款 |
| `PaidFormedGroupBuyRefundStrategy` | 已支付、已成团退款 |
| `GroupBuyRefundStrategyRouter` | 根据队伍状态路由退款策略 |
| `RefundOrderRuleFilter` | 退款责任链末端按 `tradeType` 分流 |
| `IGroupBuyRepository` | 新增拼团退款仓储能力 |
| `GroupBuyRepository` | 新增拼团退款事务 |
| `OrderRefundBehaviorEntity` / `RefundOrderResponseDTO` | 退款响应新增队伍进度 |
| `docs/数据记录-拼团锁单数据.md` | 补充拼团退款数据关系 |

## 业务流程

1. 用户调用 `/api/trade/orders/{orderId}/refund/mock`。
2. 退款责任链先做公共校验：
   - 订单存在；
   - 订单属于当前用户；
   - 已退款订单返回重复退款；
   - 只有 `PAID` 状态可退款。
3. 判断订单类型：
   - `NORMAL`：沿用普通退款逻辑；
   - `GROUP_BUY`：进入拼团退款策略路由。
4. 策略路由：
   - 队伍 `IN_PROGRESS`：走已支付未成团退款；
   - 队伍 `SUCCESS` 或 `COMPLETE_FAIL`：走已支付已成团退款；
   - 其他状态拒绝退款。
5. 已支付未成团退款：
   - `dining_order -> REFUNDED`；
   - `group_buy_order_list -> REFUNDED`；
   - `lock_count - 1`；
   - `complete_count - 1`；
   - `group_buy_activity.stock + 1`；
   - 队伍保持 `IN_PROGRESS`。
6. 已支付已成团退款：
   - `dining_order -> REFUNDED`；
   - `group_buy_order_list -> REFUNDED`；
   - `lock_count - 1`；
   - `complete_count - 1`；
   - 如果退款前队伍 `complete_count > 1`，队伍变为 `COMPLETE_FAIL`；
   - 如果退款前队伍 `complete_count = 1`，队伍变为 `FAILED`；
   - 按 xfg 逻辑，已成团退款当前不恢复活动库存。

## 本地验证

### 编译和打包

执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：均为 `BUILD SUCCESS`。

### 已支付未成团退款

开团并支付后，队伍状态：

```json
{
  "teamStatus": "IN_PROGRESS",
  "lockCount": 1,
  "completeCount": 1
}
```

退款返回：

```json
{
  "orderId": 26,
  "orderStatus": "REFUNDED",
  "refundBehavior": "success",
  "teamStatus": "IN_PROGRESS",
  "lockCount": 0,
  "completeCount": 0
}
```

活动库存验证：退款前后恢复到同一值。

### 已支付已成团退款

两人支付成团后，第二笔支付返回：

```json
{
  "teamStatus": "SUCCESS",
  "lockCount": 2,
  "completeCount": 2
}
```

第一笔退款返回：

```json
{
  "orderId": 27,
  "orderStatus": "REFUNDED",
  "teamStatus": "COMPLETE_FAIL",
  "lockCount": 1,
  "completeCount": 1
}
```

第二笔退款返回：

```json
{
  "orderId": 28,
  "orderStatus": "REFUNDED",
  "teamStatus": "FAILED",
  "lockCount": 0,
  "completeCount": 0
}
```

### 非法状态退款

`WAIT_PAY` 拼团订单直接退款返回：

```json
{
  "code": "400",
  "message": "order status can not refund",
  "data": null
}
```

随后走取消接口，验证订单正常取消回滚。

### 落库结果

```text
dining_order:
26 GROUP_BUY REFUNDED
27 GROUP_BUY REFUNDED
28 GROUP_BUY REFUNDED
29 GROUP_BUY CANCELED

group_buy_order:
GBT9DF8DA7649B74379 lock_count=0 complete_count=0 team_status=IN_PROGRESS
GBTEF40069CC1A5497D lock_count=0 complete_count=0 team_status=FAILED
GBT73BB27824BCB4002 lock_count=0 complete_count=0 team_status=IN_PROGRESS

group_buy_order_list:
order_id=26 REFUNDED
order_id=27 REFUNDED
order_id=28 REFUNDED
order_id=29 CANCELED
```

## 当前边界

当前仍然是模拟退款，没有接真实支付渠道退款单、MQ 通知任务和库存恢复消息。后续接真实支付骨架时，可以把当前策略作为退款领域逻辑复用。

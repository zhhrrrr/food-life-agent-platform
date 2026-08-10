# 开发日志 021 - trade 服务拼团支付结算链

## 本次目标

在上一阶段拼团锁单之后，继续按 xfg 拼团的支付结算逻辑推进拼团状态。当前项目仍使用现有支付入口：

```http
POST /api/trade/orders/{orderId}/pay/mock
```

接口入口不变，领域服务根据订单 `trade_type` 分流：

- `NORMAL`：只更新交易主订单为 `PAID`。
- `GROUP_BUY`：更新交易主订单、参团明细、队伍完成人数，并判断是否成团。

## 参考 xfg 拼团逻辑

| xfg 拼团逻辑 | 当前项目迁移结果 |
| --- | --- |
| `TradeSettlementOrderService.settlementMarketPayOrder` | `OrderPaySettlementService.settlementOrderPaySuccess` 中按 `tradeType` 分流 |
| `TradeSettlementRuleFilterFactory` | 继续复用已有 `OrderSettlementRuleFilterFactory` 支付规则链 |
| `OutTradeNoRuleFilter` 查询营销订单 | 当前项目用 `orderId + userId` 查询 `dining_order` |
| `SettableRuleFilter` 校验拼团有效时间 | 当前阶段沿用锁单时队伍有效性，支付后续可补精确交易时间校验 |
| `group_buy_order_list.status = 1` | `group_buy_order_list.order_status = PAID` |
| `group_buy_order.complete_count + 1` | `group_buy_order.complete_count + 1` |
| 达到目标后 `group_buy_order.status = 1` | 达到目标后 `group_buy_order.team_status = SUCCESS` |

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `OrderPaySettlementService` | 支付成功后按订单类型分流，拼团订单调用拼团仓储事务 |
| `IGroupBuyRepository` | 新增 `settlementGroupBuyPaySuccess` |
| `GroupBuyRepository` | 新增拼团支付结算事务，负责主订单、参团明细、队伍进度一致更新 |
| `GroupBuyStatusConstants` | 新增 `PAID`、`SUCCESS` |
| `GroupBuyOrderListEntity` / `GroupBuyOrderListPO` | 新增 `outTradeTime` |
| `OrderPaySettlementEntity` / `PayOrderResponseDTO` | 支付响应新增队伍进度字段 |
| `OrderController` | 支付响应映射队伍信息 |
| `docs/sql/food_trade_db.sql` | `group_buy_order_list` 新增 `out_trade_time` |

## 拼团支付业务流程

1. 用户调用 `/api/trade/orders/{orderId}/pay/mock`。
2. 支付规则链先做公共校验：
   - 用户是否登录；
   - `orderId` 是否存在；
   - 订单是否属于当前用户；
   - 订单是否处于 `WAIT_PAY`。
3. 判断订单类型：
   - 普通订单：更新 `dining_order` 为 `PAID`。
   - 拼团订单：进入拼团支付结算事务。
4. 拼团支付结算事务：
   - 更新 `dining_order.order_status = PAID`；
   - 查询 `group_buy_order_list`；
   - 更新 `group_buy_order_list.order_status = PAID`；
   - 写入 `group_buy_order_list.out_trade_time`；
   - 更新 `group_buy_order.complete_count = complete_count + 1`；
   - 如果 `complete_count >= target_count`，更新 `group_buy_order.team_status = SUCCESS`。
5. 返回支付结果，拼团订单会额外返回：
   - `teamId`
   - `activityId`
   - `teamStatus`
   - `targetCount`
   - `lockCount`
   - `completeCount`

## 当前边界

当前仍然是模拟支付入口，没有接真实第三方支付回调。后续接真实支付骨架时，回调只需要复用当前领域结算服务即可。

## 本地验证

### 编译和打包

执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：均为 `BUILD SUCCESS`。

### 表结构

本地 `group_buy_order_list` 已补充字段：

```sql
out_trade_time DATETIME DEFAULT NULL COMMENT 'external pay success time'
```

### 接口验证

验证 1：用户 A 开团并支付。

开团返回：

```json
{
  "orderId": 22,
  "teamId": "GBTDF7D56F4C2F84706",
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "targetCount": 2,
  "lockCount": 1,
  "completeCount": 0
}
```

支付返回：

```json
{
  "orderId": 22,
  "orderStatus": "PAID",
  "teamId": "GBTDF7D56F4C2F84706",
  "teamStatus": "IN_PROGRESS",
  "targetCount": 2,
  "lockCount": 1,
  "completeCount": 1
}
```

验证 2：用户 B 参团并支付。

参团返回：

```json
{
  "orderId": 23,
  "teamId": "GBTDF7D56F4C2F84706",
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "targetCount": 2,
  "lockCount": 2,
  "completeCount": 1
}
```

支付返回：

```json
{
  "orderId": 23,
  "orderStatus": "PAID",
  "teamId": "GBTDF7D56F4C2F84706",
  "teamStatus": "SUCCESS",
  "targetCount": 2,
  "lockCount": 2,
  "completeCount": 2
}
```

### 落库结果

```text
dining_order:
22 GROUP_BUY PAID
23 GROUP_BUY PAID

group_buy_order:
team_id=GBTDF7D56F4C2F84706 target_count=2 lock_count=2 complete_count=2 team_status=SUCCESS

group_buy_order_list:
order_id=22 order_status=PAID out_trade_time=2026-08-10 10:35:55
order_id=23 order_status=PAID out_trade_time=2026-08-10 10:36:09
```

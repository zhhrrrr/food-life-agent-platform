# 开发日志 025 - trade 服务拼团查询接口

## 本次目标

补齐拼团的读模型能力，让前端和后续 Agent 能看见拼团状态，而不是只能调用动作接口。

本次新增三个查询接口：

```http
GET /api/trade/group-buy/teams?packageId=1&limit=20
GET /api/trade/group-buy/teams/{teamId}
GET /api/trade/group-buy/orders?lastId=15&pageSize=10
```

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `GroupBuyQueryService` | 拼团查询领域服务 |
| `GroupBuyTeamView` | 队伍读模型，包含剩余人数和是否可参团 |
| `GroupBuyParticipantView` | 队伍参与人读模型 |
| `GroupBuyUserOrderView` | 我的拼团记录读模型，包含 Agent 友好动作字段 |
| `GroupBuyTeamQueryResult` | 可参团队伍列表结果 |
| `GroupBuyUserOrderQueryResult` | 我的拼团记录分页结果 |
| `IGroupBuyRepository` | 新增拼团查询仓储能力 |
| `GroupBuyRepository` | 基于 MyBatis-Plus 聚合查询队伍、参团明细和主订单 |
| `GroupBuyQueryController` | 新增 HTTP 查询入口 |
| `GroupBuyTeamListResponseDTO` | 可参团队伍列表响应 |
| `GroupBuyTeamDetailResponseDTO` | 队伍详情响应 |
| `UserGroupBuyOrderListResponseDTO` | 我的拼团记录响应 |
| `docs/数据记录-拼团锁单数据.md` | 补充拼团查询读模型说明 |

## 查询规则

### 可参团队伍列表

只返回满足以下条件的队伍：

1. `package_id = packageId`
2. `team_status = IN_PROGRESS`
3. `valid_end_time > 当前时间`
4. `lock_count < target_count`

返回字段包括：

- `remainingCount`
- `canJoin`
- `validEndTime`
- `targetCount / lockCount / completeCount`

### 队伍详情

队伍详情会聚合：

1. `group_buy_order` 队伍主信息。
2. `group_buy_order_list` 参团明细。
3. `dining_order` 主订单状态。

参与人返回：

- `userId`
- `orderId`
- `orderNo`
- `groupBuyOrderStatus`
- `orderStatus`
- `outTradeTime`

### 我的拼团记录

按 `group_buy_order_list.id DESC` 分页，`lastId` 也是 `group_buy_order_list.id`。

返回 Agent 友好动作字段：

- `canPay`
- `canCancel`
- `canRefund`

判断规则：

```text
canPay:
主订单 WAIT_PAY
参团明细 LOCKED
队伍 IN_PROGRESS
队伍未过期

canCancel:
主订单 WAIT_PAY
参团明细 LOCKED
队伍 IN_PROGRESS

canRefund:
主订单 PAID
参团明细 PAID
队伍状态 IN_PROGRESS / SUCCESS / COMPLETE_FAIL
```

## 本地验证

已执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`。

### 开团后查询

测试队伍：`GBT112650013C304929`

A 用户开团后，队伍详情：

```json
{
  "teamId": "GBT112650013C304929",
  "targetCount": 2,
  "lockCount": 1,
  "completeCount": 0,
  "remainingCount": 1,
  "teamStatus": "IN_PROGRESS",
  "canJoin": true
}
```

A 用户我的拼团记录：

```json
{
  "orderId": 33,
  "groupBuyOrderStatus": "LOCKED",
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "canPay": true,
  "canCancel": true,
  "canRefund": false
}
```

### A 支付后查询

```json
{
  "orderId": 33,
  "groupBuyOrderStatus": "PAID",
  "orderStatus": "PAID",
  "teamStatus": "IN_PROGRESS",
  "canPay": false,
  "canCancel": false,
  "canRefund": true
}
```

### B 参团后查询

队伍满员但未成团：

```json
{
  "teamId": "GBT112650013C304929",
  "lockCount": 2,
  "completeCount": 1,
  "remainingCount": 0,
  "teamStatus": "IN_PROGRESS",
  "canJoin": false
}
```

此时该队伍不再出现在可参团队伍列表。

### B 支付成团后查询

```json
{
  "teamId": "GBT112650013C304929",
  "lockCount": 2,
  "completeCount": 2,
  "remainingCount": 0,
  "teamStatus": "SUCCESS",
  "canJoin": false
}
```

B 用户我的拼团记录：

```json
{
  "orderId": 34,
  "groupBuyOrderStatus": "PAID",
  "orderStatus": "PAID",
  "teamStatus": "SUCCESS",
  "canPay": false,
  "canCancel": false,
  "canRefund": true
}
```

# 开发日志 024 - trade 服务拼团超时补偿

## 本次目标

补齐拼团生产环境里必须有的兜底链路：队伍超过 `valid_end_time` 仍未成团时，系统自动把队伍置为失败，并处理队伍下未支付和已支付订单。

手动触发入口：

```http
POST /api/trade/group-buy/timeout/compensate?limit=50
```

后台定时任务：

```yaml
food.trade.group-buy.timeout-compensation.enabled: true
food.trade.group-buy.timeout-compensation.fixed-delay-ms: 60000
```

## 本次代码变更

| 文件 | 说明 |
| --- | --- |
| `GroupBuyTimeoutCompensationService` | 拼团超时补偿领域服务，负责扫描和汇总补偿结果 |
| `GroupBuyTimeoutCompensateResult` | 本次补偿汇总结果 |
| `GroupBuyTimeoutCompensateDetail` | 单个队伍补偿明细 |
| `IGroupBuyRepository` | 新增超时队伍查询和单队伍补偿事务能力 |
| `GroupBuyRepository` | 实现超时补偿事务，并给支付结算增加队伍未过期校验 |
| `GroupBuyCompensationController` | 提供手动触发补偿 HTTP 入口 |
| `GroupBuyTimeoutCompensationJob` | Java 侧定时任务，后续可替换为 XXL-JOB、MQ 延迟消息或 Agent 调度 |
| `TradeApplication` | 开启 Spring Scheduling |
| `application.yml` | 增加补偿任务开关和扫描间隔 |
| `GroupBuyTimeoutCompensationResponseDTO` | 补偿接口响应 DTO |
| `docs/数据记录-拼团锁单数据.md` | 补充超时补偿的数据写入关系 |

## 业务流程

1. 定时任务或手动接口触发补偿。
2. 查询满足条件的队伍：
   - `group_buy_order.team_status = IN_PROGRESS`
   - `group_buy_order.valid_end_time <= 当前时间`
3. 对每个队伍开启单独事务处理。
4. 先把队伍从 `IN_PROGRESS` CAS 更新为 `FAILED`。
5. 队伍计数归零：
   - `lock_count = 0`
   - `complete_count = 0`
6. 处理队伍下仍未完成的参团明细：
   - `LOCKED` 明细：主订单 `WAIT_PAY -> CANCELED`，参团明细 `LOCKED -> CANCELED`
   - `PAID` 明细：主订单 `PAID -> REFUNDED`，参团明细 `PAID -> REFUNDED`
7. 按处理明细数恢复活动库存：
   - `group_buy_activity.stock = stock + 补偿明细数`
8. 返回本次扫描数量、实际补偿队伍数、取消订单数、退款订单数和恢复库存数。

## 支付侧同步约束

为了避免“队伍已过期但补偿任务还没扫到，用户仍支付成功”的问题，本次同步调整了拼团支付结算：

```java
group_buy_order.valid_end_time > out_trade_time
```

也就是说，拼团支付成功结算时，只有队伍还在有效期内才允许 `complete_count + 1`。

## 当前边界

- 当前退款仍是模拟退款，只更新本地订单状态；真实支付网关退款、退款单、通知任务后续接支付骨架时再补。
- 当前定时任务是单机 Spring Scheduling，适合本地和第一阶段演示；生产可换成 XXL-JOB、MQ 延迟消息或 Agent 调度工具。
- 超时补偿只处理未成团队伍，已成团队伍售后仍走拼团退款策略。

## 本地验证

已执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
mvn -pl food-trade-service/food-trade-app -am package -DskipTests
mvn -pl food-trade-service/food-trade-app -am test
```

结果：`BUILD SUCCESS`。

### 混合队伍超时补偿

测试队伍：`GBTC838C1B9F2ED4E12`

构造数据：

- 订单 `30`：已支付，`PAID`
- 订单 `31`：已锁单未支付，`WAIT_PAY / LOCKED`
- 手动把队伍 `valid_end_time` 改为当前时间之前

补偿接口返回：

```json
{
  "scannedTeamCount": 1,
  "compensatedTeamCount": 1,
  "canceledOrderCount": 1,
  "refundedOrderCount": 1,
  "restoredStockCount": 2,
  "details": [
    {
      "teamId": "GBTC838C1B9F2ED4E12",
      "teamStatus": "FAILED",
      "beforeLockCount": 2,
      "beforeCompleteCount": 1,
      "canceledOrderCount": 1,
      "refundedOrderCount": 1,
      "restoredStockCount": 2
    }
  ]
}
```

数据库结果：

```text
group_buy_order:
GBTC838C1B9F2ED4E12  lock_count=0  complete_count=0  team_status=FAILED

dining_order:
30  REFUNDED
31  CANCELED

group_buy_order_list:
30  REFUNDED
31  CANCELED
```

活动库存从 `94 -> 94`，说明创建队伍扣减的 2 份库存已恢复。

### 过期队伍支付保护

测试队伍：`GBTF0B44B8DC58F41C6`

构造数据：

- 创建订单 `32`
- 手动把队伍 `valid_end_time` 改为当前时间之前
- 再调用模拟支付

支付返回：

```json
{
  "code": "400",
  "message": "group buy team can not settlement",
  "data": null
}
```

随后补偿结果：

```text
dining_order: 32 CANCELED
group_buy_order_list: 32 CANCELED
group_buy_order: GBTF0B44B8DC58F41C6 lock_count=0 complete_count=0 team_status=FAILED
```

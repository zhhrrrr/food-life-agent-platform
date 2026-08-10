# 开发日志 020 - trade 服务拼团锁单接口

## 本次目标

在 `food-trade-service` 内新增拼团锁单主流程，复用已经迁移的 xfg 责任链模板，实现美食套餐的拼团下单入口。

## 代码变更

| 位置 | 说明 |
| --- | --- |
| `food-trade-domain/.../groupbuy/model` | 新增拼团活动、队伍、参团明细、锁单命令、锁单上下文、锁单结果、锁单聚合对象 |
| `food-trade-domain/.../groupbuy/filter` | 新增拼团锁单责任链节点：参数校验、活动可用性、用户参与限制、队伍可用性、套餐交易快照 |
| `food-trade-domain/.../groupbuy/factory` | 新增 `GroupBuyLockRuleFilterFactory`，组装一条多节点拼团锁单责任链 |
| `food-trade-domain/.../groupbuy/service` | 新增 `GroupBuyLockOrderService`，执行规则链并构建锁单聚合 |
| `food-trade-infrastructure/.../repository` | 新增 `GroupBuyRepository`，事务保存拼团锁单聚合 |
| `food-trade-api/.../dto` | 新增拼团下单请求和响应 DTO |
| `food-trade-trigger/.../OrderController.java` | 新增 `POST /api/trade/orders/group-buy` |
| `docs/sql/food_trade_db.sql` | 新增拼团活动、队伍、参团明细建表和本地初始化数据 |

## 业务流程

1. 用户携带黑马点评 token 请求 `POST /api/trade/orders/group-buy`。
2. 控制器从 `UserHolder` 获取登录用户。
3. 拼团锁单责任链依次校验：
   - 用户、套餐、数量是否有效；
   - 套餐是否存在有效拼团活动；
   - 活动状态、时间、库存是否可用；
   - 用户是否超过参与次数限制；
   - 如果传入 `teamId`，校验队伍是否存在、未过期、未满员；
   - 远程查询套餐交易快照并校验套餐可售和库存。
4. 领域服务构建锁单聚合：
   - `dining_order` 主订单；
   - `dining_order_item` 订单明细；
   - `group_buy_order` 拼团队伍；
   - `group_buy_order_list` 用户参团明细。
5. 仓储在一个事务内扣活动库存、写订单、写明细、写拼团关系。

## 和 xfg 拼团的对应关系

| xfg 拼团 | 当前项目 |
| --- | --- |
| `TradeLockOrderService.lockMarketPayOrder` | `GroupBuyLockOrderService.lockOrder` |
| `TradeLockRuleFilterFactory` | `GroupBuyLockRuleFilterFactory` |
| `ActivityUsabilityRuleFilter` | `GroupBuyActivityUsabilityRuleFilter` |
| `UserTakeLimitRuleFilter` | `GroupBuyUserTakeLimitRuleFilter` |
| `TeamStockOccupyRuleFilter` | 当前先用数据库乐观更新占用活动库存和队伍名额，后续再补 Redis 队伍库存恢复 |
| `GroupBuyOrderAggregate` | `GroupBuyLockAggregate` |

## 当前边界

本次完成的是锁单，不是完整成团。后续需要继续做拼团支付成功结算：订单支付后更新参团明细、队伍完成人数，并在达到 `target_count` 后更新队伍为成团成功。

## 本地验证

### 编译验证

执行：

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
```

结果：`BUILD SUCCESS`。

### 数据库验证

执行 `docs/sql/food_trade_db.sql` 后，本地 `food_trade_db` 新增：

- `group_buy_activity`
- `group_buy_order`
- `group_buy_order_list`

初始化活动：

| 字段 | 值 |
| --- | --- |
| `id` | `1` |
| `package_id` | `1` |
| `activity_name` | `双人火锅套餐拼团` |
| `target_count` | `2` |
| `group_price` | `12800` |
| `stock` | `100` 初始，接口验证后扣减 |

### 接口验证

服务启动：

- `food-user-service`: `8101`
- `food-business-service`: `8201`
- `food-trade-service`: `8301`

验证 1：开新团。

请求：

```http
POST /api/trade/orders/group-buy
authorization: {token}

{
  "packageId": 1,
  "quantity": 1
}
```

返回核心数据：

```json
{
  "orderId": 20,
  "teamId": "GBT27970BE09EC0409F",
  "payAmount": 12800,
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "targetCount": 2,
  "lockCount": 1,
  "completeCount": 0
}
```

验证 2：另一个用户加入已有团。

请求：

```http
POST /api/trade/orders/group-buy
authorization: {anotherToken}

{
  "packageId": 1,
  "quantity": 1,
  "teamId": "GBT27970BE09EC0409F"
}
```

返回核心数据：

```json
{
  "orderId": 21,
  "teamId": "GBT27970BE09EC0409F",
  "payAmount": 12800,
  "orderStatus": "WAIT_PAY",
  "teamStatus": "IN_PROGRESS",
  "targetCount": 2,
  "lockCount": 2,
  "completeCount": 0
}
```

验证 3：第三个用户继续加入满员团。

返回：

```json
{
  "code": "400",
  "message": "group buy team full",
  "data": null
}
```

### 落库结果

开团后订单详情接口可查到：

- `dining_order.trade_type = GROUP_BUY`
- `dining_order.order_status = WAIT_PAY`
- `dining_order.total_amount = 16800`
- `dining_order.pay_amount = 12800`
- `dining_order_item.actual_price = 12800`
- `group_buy_order.lock_count = 2`
- `group_buy_order_list.order_status = LOCKED`
- `group_buy_activity.stock` 从 `100` 扣到 `98`

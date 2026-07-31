# 开发日志-017-trade服务用户订单列表接口

## 本次目标

继续沿着订单主流程开发，新增用户订单列表查询能力。

这一步参考 `s-pay-mall-ddd-market` 里的订单列表逻辑，不重新设计一套分页方式。

## 对应现成项目逻辑

参考文件：

```text
s-pay-mall-ddd-market/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/AliPayController.java
s-pay-mall-ddd-market/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/AbstractOrderService.java
s-pay-mall-ddd-market/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/repository/IOrderRepository.java
s-pay-mall-ddd-market/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/OrderRepository.java
s-pay-mall-ddd-market/s-pay-mall-ddd-app/src/main/resources/mybatis/mapper/pay_order_mapper.xml
```

s-pay 原逻辑：

```text
1. 入参 userId、lastId、pageSize
2. 查询 pageSize + 1 条订单
3. 如果结果数量大于 pageSize，说明 hasMore = true
4. 截取前 pageSize 条返回
5. lastId 返回当前页最后一条订单 ID
6. 下一页用 lastId 继续查询 id > lastId 的数据
```

本项目迁移后：

```text
1. userId 不允许前端传，从 UserHolder 登录态获取
2. lastId、pageSize 从 query 参数获取
3. 仓储按 userId 查询 dining_order
4. 使用 id > lastId + order by id asc + limit
5. 多查一条判断 hasMore
6. 列表项补充 dining_order_item 的店铺名、套餐名、封面快照
```

## 新增接口

```text
GET /api/trade/orders?lastId=&pageSize=
```

示例：

```text
GET /api/trade/orders?pageSize=10
GET /api/trade/orders?lastId=12&pageSize=10
```

返回示例：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "orders": [
      {
        "orderId": 10,
        "orderNo": "NO178548609433612",
        "userId": 12,
        "shopId": 1,
        "shopNameSnapshot": "Hot Spicy Kitchen",
        "packageId": 1,
        "packageNameSnapshot": "Double Hot Pot Set",
        "coverImageSnapshot": "https://example.com/package/hot-pot.jpg",
        "quantity": 1,
        "totalAmount": 16800,
        "payAmount": 16800,
        "tradeType": "NORMAL",
        "orderStatus": "PAID",
        "createTime": "2026-07-31T16:20:00"
      }
    ],
    "hasMore": false,
    "lastId": 10
  }
}
```

## 新增文件

```text
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/OrderListResponseDTO.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderListResult.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderSummaryEntity.java
```

## 修改文件

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/repository/IOrderRepository.java
```

新增：

```text
listUserOrders(Long userId, Long lastId, Integer pageSize)
```

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OrderRepository.java
```

新增按用户游标分页查询：

```text
where user_id = ?
and id > lastId
order by id asc
limit pageSize
```

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

新增：

```text
queryUserOrderList(Long userId, Long lastId, Integer pageSize)
```

业务规则：

```text
pageSize 为空或小于等于 0 时默认 10
pageSize 最大 50
实际查询 pageSize + 1 条
hasMore 根据是否多查出一条判断
lastId 返回当前页最后一条 orderId
```

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

新增：

```text
GET /api/trade/orders
```

## 当前业务流

```text
1. 用户登录
2. 创建普通购买订单
3. 可取消订单
4. 可模拟支付成功
5. 可查询订单详情
6. 可分页查询自己的订单列表
```

## 为什么这一步先做订单列表

真实支付、支付单表、支付回调验签后面再接。

当前继续做订单列表的原因：

```text
1. s-pay 现成项目里已经有订单列表逻辑，可直接迁移思想
2. 前端和 Agent 都需要查询用户历史订单
3. 后续做退款、核销、评价都需要先能定位用户订单
4. 不新增微服务，不增加架构复杂度
```

## 验证结果

已执行：

```bash
mvn -q test
```

结果：

```text
通过。
```

已完成本地接口联调：

```text
GET /api/trade/orders?pageSize=2：通过
GET /api/trade/orders?lastId={上一页lastId}&pageSize=2：通过
未登录查询订单列表：通过
```

实测记录：

```text
用户 12 查询第一页：
code = 0000
pageSize = 2
返回 2 条订单
hasMore = false
lastId = 12
列表项包含 packageNameSnapshot = Two-person Hot Pot Set

用户 12 查询下一页：
code = 0000
返回 0 条订单
hasMore = false
lastId = null

未登录查询：
HTTP 401
{"code":"401","message":"用户未登录"}
```

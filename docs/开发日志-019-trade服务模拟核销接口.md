# 开发日志-019-trade服务模拟核销接口

## 本次目标

新增美食套餐订单的模拟核销能力。

这一步对应真实业务里的：

```text
用户购买套餐 -> 支付成功 -> 到店出示订单/券码 -> 店员核销 -> 订单使用完成
```

## 对应现成项目逻辑

参考 `s-pay-mall-ddd-market`：

```text
s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/goods/service/GoodsService.java
s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/GoodsRepository.java
s-pay-mall-ddd-app/src/main/resources/mybatis/mapper/pay_order_mapper.xml
```

s-pay 里的核心逻辑：

```text
changeOrderDealDone(orderId)
  -> repository.changeOrderDealDone(orderId)
  -> update pay_order set status = 'DEAL_DONE'
```

参考黑马点评：

```text
hm-dianping/src/main/java/com/hmdp/entity/VoucherOrder.java
```

里面已有：

```text
status: 3 表示已核销
useTime: 核销时间
```

本项目迁移后：

```text
status: USED
useTime: 套餐核销时间
```

## 新增接口

```text
POST /api/trade/orders/{orderId}/use/mock
```

返回示例：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "userId": 12,
    "orderId": 16,
    "orderNo": "NO178548609433616",
    "orderStatus": "USED",
    "useBehavior": "success",
    "useTime": "2026-07-31T16:50:00"
  }
}
```

重复核销返回：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "orderStatus": "USED",
    "useBehavior": "repeat"
  }
}
```

## 新增字段

```text
dining_order.use_time
```

建表 SQL 已更新：

```text
docs/sql/food_trade_db.sql
```

已有本地库需要执行：

```sql
ALTER TABLE food_trade_db.dining_order
  ADD COLUMN use_time DATETIME DEFAULT NULL COMMENT 'package use time'
  AFTER order_status;
```

## 新增订单状态

```text
USED
```

当前状态：

```text
WAIT_PAY
PAID
USED
CANCELED
REFUNDED
```

当前状态流转：

```text
下单：null -> WAIT_PAY
取消：WAIT_PAY -> CANCELED
模拟支付：WAIT_PAY -> PAID
模拟核销：PAID -> USED
重复核销：USED -> USED，返回 repeat
模拟退款：PAID -> REFUNDED
```

## 新增文件

```text
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/UseOrderResponseDTO.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderUseCommandEntity.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderUseResult.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/use/OrderUseService.java
```

## 修改文件

```text
DiningOrderEntity
DiningOrderPO
OrderRepository
IOrderRepository
OrderDomainService
OrderController
OrderDetailResponseDTO
OrderListResponseDTO
OrderStatusConstants
docs/sql/food_trade_db.sql
```

## 业务规则

```text
1. 用户必须登录
2. 订单必须存在
3. 订单必须属于当前登录用户
4. 只有 PAID 状态可以核销
5. USED 状态重复核销返回 repeat
6. WAIT_PAY 不能核销
7. CANCELED 不能核销
8. REFUNDED 不能核销
```

## 为什么没有用责任链

这一步对齐的是 `s-pay` 的 `GoodsService.changeOrderDealDone`：

```text
一个服务方法直接更新交易完成状态。
```

核销当前没有多规则组合，不适合为了模式强行抽责任链。
后续如果接商家端权限、核销码、门店设备、风控规则，再升级成规则链更合适。

## 验证结果

```text
mvn -q test
```

结果：

```text
通过。
```

本地库已执行：

```sql
ALTER TABLE food_trade_db.dining_order
  ADD COLUMN use_time DATETIME DEFAULT NULL COMMENT 'package use time'
  AFTER order_status;
```

已完成接口联调：

```text
登录 -> 下单 -> 支付 -> 核销：通过
重复核销：通过
待支付订单核销：通过
已取消订单核销：通过
已退款订单核销：通过
未登录核销：通过
越权核销：通过
```

实测记录：

```text
主链路：
订单 16 创建后 WAIT_PAY，支付后 PAID，核销后 USED。
详情查询 orderStatus = USED，useTime 有值。
订单列表 orderStatus = USED，useTime 有值。

重复核销：
订单 16 第二次核销返回 code=0000，useBehavior=repeat，orderStatus=USED。

待支付订单核销：
订单 17 未支付核销返回 code=400，message=order status can not use。

已取消订单核销：
订单 18 取消后核销返回 code=400，message=order status can not use。

已退款订单核销：
订单 19 退款后核销返回 code=400，message=order status can not use。

越权核销：
用户 13 核销用户 12 的订单 16，返回 code=400，message=order not found。

未登录核销：
HTTP 401
{"code":"401","message":"用户未登录"}
```

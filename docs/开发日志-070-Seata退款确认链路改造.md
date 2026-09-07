# 开发日志 070 - Seata 退款确认链路改造

## 背景

上一版 Seata 示例落在“运营库存调整”上，技术上能成立，但业务归属不够自然：套餐库存调整更偏 business-service 自身能力，为了展示 Seata 让 trade-service 额外参与，讲起来会有一点生硬。

本次改造把 Seata 主案例迁移到“退款确认后内部状态一致性”：

```text
第三方退款成功 / 平台模拟退款确认
  -> trade-service 修改订单、支付单、优惠券、拼团/秒杀状态
  -> business-service 回滚普通套餐销量并释放套餐库存
  -> 任意一步失败，Seata AT 回滚内部 MySQL 状态
```

## 本次新增与变更

### 1. 新增退款确认应用服务

文件：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/app/RefundConfirmApplicationService.java
```

职责：

- 作为退款确认用例的应用服务层入口
- 使用 `@GlobalTransactional(name = "food-refund-confirm", rollbackFor = Exception.class)` 开启 Seata 全局事务
- 调用 `OrderDomainService.refundOrderMock`

设计原因：

- Seata 属于应用层事务编排能力，不应该散落到领域模型里
- 领域服务继续表达退款业务，应用服务负责跨服务事务边界

### 2. 运营库存调整不再作为 Seata 主案例

文件：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/app/OperationStockAdjustmentApplicationService.java
```

变更：

- 移除 `@GlobalTransactional`
- 该业务保留为普通运营库存调整能力，不再承担 Seata 展示主线

### 3. 退款接口改为走 Seata 退款确认应用服务

文件：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

变更：

- 注入 `RefundConfirmApplicationService`
- 新增正式接口：

```text
POST /api/trade/orders/{orderId}/refund/confirm
```

- 保留旧接口：

```text
POST /api/trade/orders/{orderId}/refund/mock
```

旧接口内部直接复用新的 `confirmRefund`，避免前端或旧脚本立刻失效。

### 4. 退款领域逻辑补齐支付单退款和库存/销量回滚

文件：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/filter/RefundOrderRuleFilter.java
```

变更：

- 注入 `IPaymentOrderRepository`
- 注入 `IBusinessPackagePort`
- 退款成功后统一处理：
  - 支付单 `SUCCESS -> REFUNDED`
  - 优惠券退回
  - 普通订单回滚套餐销量
  - 普通订单释放套餐库存

普通订单退款现在会同步调用 business-service：

```text
businessPackagePort.rollbackPackageSold(packageId, quantity, "REFUND:{orderNo}:ROLLBACK_SOLD")
businessPackagePort.releasePackageStock(packageId, quantity, "REFUND:{orderNo}:RELEASE_STOCK")
```

这里复用了 business-service 已有的库存幂等记录：

```text
package_stock_change_record.operation_id
```

### 5. 退款领域服务增加本地事务

文件：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/refund/OrderRefundService.java
```

变更：

- 增加 `@Transactional(rollbackFor = Exception.class)`

作用：

- Seata 关闭时，至少保证 trade-service 内部订单、支付单、优惠券状态本地回滚
- Seata 开启时，作为全局事务中的本地分支事务参与 AT 管理

### 6. 支付单增加 REFUNDED 状态

文件：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/payment/constant/PaymentOrderStatusConstants.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/payment/repository/IPaymentOrderRepository.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/PaymentOrderRepository.java
```

变更：

- 新增 `PaymentOrderStatusConstants.REFUNDED`
- 新增 `markPayRefunded(orderId, userId, fromStatus)`
- 支付单状态从 `SUCCESS` 才允许变更为 `REFUNDED`

兼容处理：

- 如果本地 mock 支付没有生成 `payment_order`，退款不会强制失败
- 如果存在支付单但状态不是 `SUCCESS/REFUNDED`，退款失败

### 7. 退款响应补充关键结果

文件：

```text
food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/RefundOrderResponseDTO.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderRefundBehaviorEntity.java
```

新增字段：

```text
paymentRefunded
packageStockRolledBack
packageStockReleased
```

前端或接口测试可以直接看到退款确认是否处理了支付单和普通套餐库存。

### 8. SQL 迁移

文件：

```text
docs/sql/food_trade_db_migration_070_refund_confirm_seata.sql
docs/sql/food_trade_db.sql
```

变更：

- `payment_order.pay_status` 注释补充 `REFUNDED`

## 改造后的业务流程

### 普通订单退款确认

```text
前端 / 测试请求
  -> POST /api/trade/orders/{orderId}/refund/confirm
  -> RefundConfirmApplicationService 开启 Seata 全局事务
  -> OrderDomainService.refundOrderMock
  -> OrderRefundService 进入退款责任链
  -> 加载订单
  -> 判断是否重复退款
  -> 订单 PAID -> REFUNDED
  -> 支付单 SUCCESS -> REFUNDED
  -> 优惠券 USED -> UNUSED
  -> Feign 调 business-service 回滚 sold
  -> Feign 调 business-service 释放 stock
  -> 发送退款相关 MQ 事件
```

### 拼团订单退款确认

```text
订单 PAID -> REFUNDED
拼团参与单 PAID -> REFUNDED
拼团队伍 lock_count / complete_count 回退
拼团活动库存回补
支付单 SUCCESS -> REFUNDED
优惠券如存在则退回
```

当前拼团库存主要在 trade-service 的拼团活动库存中，所以不强行调用 business-service 套餐库存。

### 秒杀订单退款确认

```text
订单 PAID -> REFUNDED
秒杀订单 PAID -> REFUNDED
秒杀活动库存回补
Redis 用户占用释放
支付单 SUCCESS -> REFUNDED
优惠券如存在则退回
```

当前秒杀库存主要在 trade-service + Redis 中，所以不强行调用 business-service 套餐库存。

## 为什么这个案例比运营库存调整更适合 Seata

退款确认天然需要保证内部状态一致：

```text
不能订单已退款，但支付单还是 SUCCESS
不能支付单已退款，但优惠券没退
不能普通套餐销量没回滚，但订单已经 REFUNDED
不能库存释放成功，但订单退款失败
```

它满足 Seata AT 的典型条件：

- 多个本地 MySQL 事务
- 跨 trade-service 与 business-service
- 低于下单/秒杀的并发量
- 希望接口返回时内部状态已经一致
- 不把第三方支付接口纳入数据库事务，只处理“外部退款成功后的内部确认”

## 后续建议

后续可以继续补一个 `refund_order` 退款单表，把退款从行为结果升级为完整聚合：

```text
refund_order
refund_no
order_id
pay_order_no
refund_amount
refund_status
refund_reason
```

这样退款链路会更接近真实生产系统。

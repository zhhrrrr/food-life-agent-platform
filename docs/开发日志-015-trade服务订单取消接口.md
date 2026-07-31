# 开发日志 015：trade 服务订单取消接口

## 一、本次开发目标

本次在 `feature/trade-order-cancel` 分支开发订单取消能力。

目标：

```text
1. 支持用户取消自己的待支付订单
2. 只允许 WAIT_PAY 状态取消
3. 取消后订单状态变更为 CANCELED
4. 防止用户取消别人的订单
5. 防止重复取消
```

## 二、新增接口

接口：

```http
POST /api/trade/orders/{orderId}/cancel
authorization: {token}
```

返回：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "NO...",
    "orderStatus": "CANCELED"
  }
}
```

## 三、业务规则

取消订单规则：

```text
1. 用户必须登录
2. orderId 不能为空
3. 订单必须存在
4. 订单必须属于当前登录用户
5. 只有 WAIT_PAY 状态可以取消
6. 更新状态时带上旧状态条件，防止并发状态错乱
```

状态流转：

```text
WAIT_PAY -> CANCELED
```

## 四、新增 DTO 和领域结果

API DTO：

```text
food-trade-api/src/main/java/com/foodlife/trade/api/dto/CancelOrderResponseDTO.java
```

领域结果：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/CancelOrderResult.java
```

字段：

```text
orderId
orderNo
orderStatus
```

## 五、领域服务变更

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

新增方法：

```text
cancelOrder(Long orderId, Long userId)
```

处理流程：

```text
1. 校验 userId
2. 校验 orderId
3. 按 orderId + userId 查询订单
4. 校验订单存在
5. 校验订单状态为 WAIT_PAY
6. 调仓储更新 WAIT_PAY -> CANCELED
7. 返回取消结果
```

## 六、仓储层变更

仓储接口：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/repository/IOrderRepository.java
```

新增：

```text
updateOrderStatus(Long orderId, String fromStatus, String toStatus)
```

基础设施实现：

```text
food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OrderRepository.java
```

更新条件：

```text
id = orderId
order_status = fromStatus
```

更新字段：

```text
order_status = toStatus
update_time = now
```

## 七、Controller 变更

文件：

```text
food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

新增：

```text
cancelOrder(@PathVariable Long orderId)
```

用户 ID 来源：

```text
UserHolder.getUserId()
```

不接受前端传 userId。

## 八、验证计划

执行：

```bash
mvn -q test
mvn -q -pl :food-trade-app -am -DskipTests package
```

接口验证：

```text
1. 登录用户创建普通购买订单
2. 当前用户取消订单，期望成功
3. 查询订单详情，状态应为 CANCELED
4. 重复取消，期望失败
5. 其他用户取消该订单，期望失败
6. 未登录取消，期望 401
```

## 九、本次结论

trade-service 当前订单生命周期新增：

```text
WAIT_PAY -> CANCELED
```

后续可以继续扩展：

```text
WAIT_PAY -> PAID
WAIT_PAY -> CLOSED
PAID -> REFUNDED
```

# 开发日志 008：trade 服务订单详情查询

## 一、本次开发目标

本次在 `feature/trade-order-query` 分支开发订单详情查询能力。

目标：

```text
1. 用户创建订单后可以查询订单详情
2. 查询结果包含订单主信息
3. 查询结果包含订单明细快照
4. 用户只能查询自己的订单
5. 未登录用户不能查询订单
```

## 二、新增接口

接口：

```http
GET /api/trade/orders/{orderId}
authorization: {token}
```

示例：

```http
GET /api/trade/orders/2
```

## 三、新增 DTO

### OrderDetailResponseDTO

文件：

```text
food-trade-api/src/main/java/com/foodlife/trade/api/dto/OrderDetailResponseDTO.java
```

字段：

```text
orderId
orderNo
userId
shopId
packageId
quantity
totalAmount
payAmount
tradeType
orderStatus
createTime
items
```

### OrderItemResponseDTO

文件：

```text
food-trade-api/src/main/java/com/foodlife/trade/api/dto/OrderItemResponseDTO.java
```

字段：

```text
itemId
shopId
shopNameSnapshot
packageId
packageNameSnapshot
packageDescriptionSnapshot
coverImageSnapshot
packagePriceSnapshot
actualPrice
quantity
useRuleSnapshot
```

## 四、新增领域模型

### OrderDetailEntity

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderDetailEntity.java
```

作用：

```text
组合订单主信息和订单明细。
```

结构：

```text
DiningOrderEntity order
List<DiningOrderItemEntity> items
```

## 五、领域服务变更

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

新增方法：

```text
queryOrderDetail(Long orderId, Long userId)
```

业务规则：

```text
1. userId 不能为空
2. orderId 不能为空
3. 按 orderId + userId 查询订单
4. 查询不到则返回 order not found
5. 查询订单明细并组装详情
```

为什么使用 `orderId + userId` 查询：

```text
避免用户通过猜测 orderId 查询别人的订单。
```

## 六、仓储层变更

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/repository/IOrderRepository.java
```

新增方法：

```text
findOrderByIdAndUserId(Long orderId, Long userId)
listOrderItems(Long orderId)
```

实现文件：

```text
food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OrderRepository.java
```

实现逻辑：

```text
1. 根据 orderId + userId 查询 dining_order
2. 根据 orderId 查询 dining_order_item
3. PO 转 Entity
```

## 七、HTTP 层变更

文件：

```text
food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

新增接口：

```text
GET /api/trade/orders/{orderId}
```

Controller 逻辑：

```text
1. 从 UserHolder 获取 userId
2. 调用 OrderDomainService.queryOrderDetail
3. 将 OrderDetailEntity 转成 OrderDetailResponseDTO
4. 返回订单详情
```

## 八、联调验证

启动服务：

```text
food-user-service: 8101
food-business-service: 8201
food-trade-service: 8301
```

三个服务健康检查均通过。

创建新订单：

```text
phone: 13800138002
packageId: 1
quantity: 1
```

创建结果：

```text
orderId: 2
orderNo: NO17853980316893
```

查询订单详情：

```http
GET /api/trade/orders/2
authorization: a806da46d7754ed9a0c3de789b1bcff3
```

返回：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "orderId": 2,
    "orderNo": "NO17853980316893",
    "userId": 3,
    "shopId": 1,
    "packageId": 1,
    "quantity": 1,
    "totalAmount": 16800,
    "payAmount": 16800,
    "tradeType": "NORMAL",
    "orderStatus": "WAIT_PAY",
    "items": [
      {
        "itemId": 2,
        "shopNameSnapshot": "Chengdu Spicy Hot Pot",
        "packageNameSnapshot": "Two-person Hot Pot Set",
        "packagePriceSnapshot": 16800,
        "actualPrice": 16800,
        "quantity": 1
      }
    ]
  }
}
```

## 九、鉴权验证

### 未登录查询

请求：

```http
GET /api/trade/orders/2
```

结果：

```text
HTTP 401
```

说明：

```text
未登录用户被 food-auth-starter 拦截。
```

### 其他用户查询

使用另一个手机号登录：

```text
13800138003
```

查询订单：

```http
GET /api/trade/orders/2
authorization: other-user-token
```

返回：

```json
{"code":"404","message":"order not found","data":null}
```

说明：

```text
其他用户不能查询不属于自己的订单。
```

## 十、验证结果

执行：

```bash
mvn -q test
mvn -q -pl :food-trade-app -am -DskipTests package
```

结果：

```text
均通过。
```

## 十一、本次结论

本次完成了订单详情查询能力。

当前 `food-trade-service` 已支持：

```text
1. 普通购买下单
2. 订单主表落库
3. 订单明细快照落库
4. 订单详情查询
5. 未登录拦截
6. 订单归属校验
```

## 十二、下一步建议

下一步建议开发：

```text
订单取消接口
```

原因：

```text
当前订单创建后状态是 WAIT_PAY，
需要支持用户在支付前取消订单。
```

后续再开发：

```text
支付单模型
支付成功回调
订单超时关闭
库存扣减
```

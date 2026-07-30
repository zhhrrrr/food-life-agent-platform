# 开发日志 007：trade 服务普通购买订单

## 一、本次开发目标

本次开发第三个 Java 微服务：

```text
food-trade-service
```

第一版只实现普通购买订单，不做拼团、秒杀、支付回调。

目标链路：

```text
用户登录
  -> 携带 Token 请求 trade-service
  -> trade-service 从 UserHolder 获取 userId
  -> trade-service 调用 business-service 查询套餐交易快照
  -> 创建 dining_order
  -> 创建 dining_order_item
  -> 返回订单号和待支付金额
```

## 二、本次分支

本次按公司开发流程创建功能分支：

```text
feature/trade-normal-order
```

## 三、新增服务结构

新增服务：

```text
food-trade-service
```

模块结构：

```text
food-trade-service
  food-trade-api
  food-trade-app
  food-trade-trigger
  food-trade-domain
  food-trade-infrastructure
  food-trade-types
```

## 四、新增数据库

数据库：

```text
food_trade_db
```

SQL 文件：

```text
docs/sql/food_trade_db.sql
```

当前表：

```text
dining_order
dining_order_item
```

## 五、新增 API DTO

### CreateOrderRequestDTO

文件：

```text
food-trade-api/src/main/java/com/foodlife/trade/api/dto/CreateOrderRequestDTO.java
```

字段：

```text
packageId
quantity
```

注意：

```text
userId 不在请求 DTO 中。
userId 必须从 UserHolder 获取，防止前端伪造用户身份。
```

### CreateOrderResponseDTO

文件：

```text
food-trade-api/src/main/java/com/foodlife/trade/api/dto/CreateOrderResponseDTO.java
```

字段：

```text
orderId
orderNo
payAmount
orderStatus
```

## 六、新增领域模型

新增位置：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model
```

新增模型：

```text
PackageTradeSnapshot
DiningOrderEntity
DiningOrderItemEntity
CreateOrderCommand
CreateOrderResult
```

## 七、新增领域服务

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

当前业务规则：

```text
1. 用户必须登录
2. packageId 不能为空
3. quantity 必须大于 0
4. 套餐必须存在
5. 套餐必须上架
6. 库存必须足够
7. 订单金额由 business 快照中的 price 计算
8. 订单状态默认为 WAIT_PAY
9. 交易类型默认为 NORMAL
10. 订单明细保存店铺和套餐快照
```

## 八、新增基础设施层

新增：

```text
DiningOrderPO
DiningOrderItemPO
IDiningOrderMapper
IDiningOrderItemMapper
OrderRepository
BusinessPackagePort
```

`BusinessPackagePort` 负责调用：

```text
GET /api/package/trade-snapshot/{packageId}
```

当前配置：

```yaml
food:
  business-service:
    base-url: http://localhost:8201
```

## 九、新增 HTTP 接口

### 健康检查

```http
GET /health
```

### 普通购买下单

```http
POST /api/trade/orders/normal
authorization: {token}
Content-Type: application/json

{
  "packageId": 1,
  "quantity": 2
}
```

成功返回：

```json
{
  "code": "0000",
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "NO17853963882542",
    "payAmount": 33600,
    "orderStatus": "WAIT_PAY"
  }
}
```

未登录返回：

```text
HTTP 401
```

## 十、联调验证

启动服务：

```text
food-user-service: 8101
food-business-service: 8201
food-trade-service: 8301
```

登录用户：

```text
phone: 13800138001
```

创建订单请求：

```http
POST /api/trade/orders/normal
authorization: 98fbb236e9f34376b9fcb325081dbc47

{
  "packageId": 1,
  "quantity": 2
}
```

返回：

```json
{"code":"0000","message":"success","data":{"orderId":1,"orderNo":"NO17853963882542","payAmount":33600,"orderStatus":"WAIT_PAY"}}
```

## 十一、数据库验证

### dining_order

```text
id: 1
order_no: NO17853963882542
user_id: 2
shop_id: 1
package_id: 1
quantity: 2
total_amount: 33600
pay_amount: 33600
trade_type: NORMAL
order_status: WAIT_PAY
```

### dining_order_item

```text
order_id: 1
shop_name_snapshot: Chengdu Spicy Hot Pot
package_name_snapshot: Two-person Hot Pot Set
package_price_snapshot: 16800
actual_price: 33600
quantity: 2
use_rule_snapshot: Valid for dine-in only. Not available on public holidays.
```

## 十二、验证结果

已执行：

```bash
mvn -q test
mvn -q -pl :food-trade-app -am -DskipTests package
```

结果：

```text
均通过。
```

接口验证：

```text
登录后下单成功。
未登录下单返回 401。
```

## 十三、当前限制

当前普通订单还有以下限制：

```text
1. 未扣减套餐库存
2. 未创建支付单
3. 未实现订单取消
4. 未实现超时关闭
5. 未实现订单查询接口
6. 未接 MQ
7. 未接 Gateway
```

## 十四、下一步建议

下一步建议优先补：

```text
订单查询接口
```

原因：

```text
创建订单后需要能查询订单详情，
后续支付、Agent 客服、用户订单列表都依赖订单查询能力。
```

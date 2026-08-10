# 开发日志 030 - trade 服务秒杀链路聚合查询

## 一、本次目标

上一阶段已经完成秒杀异步下单、请求单、本地消息、恢复补偿和库存对账。

本次继续补一个面向 Agent 的查询能力：

```text
用户只提供 requestNo 或 orderId
-> trade 服务聚合秒杀请求单、真实订单、订单明细、秒杀活动、套餐快照、库存状态
-> 返回 currentStage，让 Agent 能判断当前链路走到哪一步
```

这一步不改秒杀下单写流程，只新增读模型和查询接口。

## 二、新增接口

### 1. 按秒杀请求单查询链路

```http
GET /api/trade/seckill/order-requests/{requestNo}/trace
```

用途：

```text
用户刚提交异步秒杀后，前端或 Agent 通过 requestNo 查询：
- 请求单是否还在创建订单
- 真实订单是否已生成
- 生成后订单处于 WAIT_PAY / PAID / USED / CANCELED / REFUNDED 哪个阶段
- 当前活动库存和 Redis 库存是否一致
```

### 2. 按订单查询秒杀链路

```http
GET /api/trade/orders/{orderId}/seckill-trace
```

用途：

```text
用户只知道订单号或订单 ID 时，Agent 可以反查该订单对应的秒杀请求单和活动信息。
```

## 三、新增和改动文件

| 文件 | 作用 |
| --- | --- |
| `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/model/SeckillOrderTraceEntity.java` | 秒杀链路聚合读模型，承载请求单、订单、活动、套餐、库存 |
| `food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/SeckillOrderTraceResponseDTO.java` | HTTP 响应 DTO，面向前端和后续 Agent 工具 |
| `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/repository/ISeckillRepository.java` | 新增按 `orderId` 查询秒杀请求单的仓储接口 |
| `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/SeckillRepository.java` | 实现 `querySeckillOrderRequestByOrderId` |
| `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/seckill/service/SeckillOrderService.java` | 组装秒杀链路聚合数据和 `currentStage` |
| `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java` | 对 trigger 层暴露秒杀链路查询能力 |
| `food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java` | 新增两个 HTTP 查询入口，并映射响应 DTO |

## 四、DDD 分层说明

### 1. trigger 层

`OrderController` 只负责：

```text
接收 requestNo/orderId
读取 UserHolder 中的当前登录用户
调用领域门面
把领域聚合模型转换为 ResponseDTO
```

### 2. domain 层

`SeckillOrderService` 负责真正的业务含义：

```text
1. 校验用户是否登录。
2. 校验 requestNo 或 orderId。
3. 查询 seckill_order_request。
4. 校验请求单归属当前用户。
5. 查询 seckill_activity。
6. 远程查询套餐交易快照。
7. 如果请求单已绑定 orderId，则查询 dining_order 和 dining_order_item。
8. 查询 MySQL 活动库存、Redis 活动库存、WAIT_PAY 数量、PAID 数量。
9. 解析 currentStage。
```

### 3. infrastructure 层

`SeckillRepository` 继续负责 MyBatis-Plus 数据查询。

本次新增：

```text
seckill_order_request.order_id -> seckill_order_request
```

这样从订单详情入口也能回到秒杀请求入口。

## 五、currentStage 规则

| 条件 | currentStage |
| --- | --- |
| 请求单状态是 `INIT` 或 `PROCESSING` | `ORDER_CREATING` |
| 请求单状态是 `FAILED` | `REQUEST_FAILED` |
| 请求单成功但订单不存在 | `ORDER_UNKNOWN` |
| 订单状态是 `WAIT_PAY` | `WAIT_PAY` |
| 订单状态是 `PAID` | `PAID` |
| 订单状态是 `USED` | `USED` |
| 订单状态是 `CANCELED` | `CANCELED` |
| 订单状态是 `REFUNDED` | `REFUNDED` |

这个字段是给 Agent 用的。Agent 不需要自己根据多个表字段猜状态，可以直接基于 `currentStage` 做下一步动作推荐。

## 六、本地验证

### 1. 编译

```bash
mvn -pl food-trade-service/food-trade-app -am compile -DskipTests
```

结果：

```text
BUILD SUCCESS
```

### 2. 测试

```bash
mvn -pl food-trade-service/food-trade-app -am test
```

结果：

```text
BUILD SUCCESS
```

### 3. 打包

第一次打包失败：

```text
Unable to rename food-trade-app-1.0-SNAPSHOT.jar to food-trade-app-1.0-SNAPSHOT.jar.original
```

原因：

```text
旧 trade 服务进程正在占用 jar。
```

处理：

```text
停止旧 8301 trade 进程后重新打包。
```

最终结果：

```text
BUILD SUCCESS
```

### 4. 接口验证

验证用户：

```text
phone = 13600136061
user_id = 33
```

历史秒杀请求：

```text
requestNo = SK178635256105433
orderId = 40
orderNo = NO178635256122233
```

验证接口：

```http
GET /api/trade/seckill/order-requests/SK178635256105433/trace
GET /api/trade/orders/40/seckill-trace
```

两个接口都返回：

```text
code = 0000
requestStatus = SUCCESS
orderStatus = WAIT_PAY
tradeType = SECKILL
totalAmount = 16800
payAmount = 9800
dbStock = 17
redisStock = 17
waitPayCount = 1
paidCount = 2
currentStage = WAIT_PAY
```

说明：

```text
通过 requestNo 和 orderId 查询到的是同一条秒杀链路。
```

## 七、对 Agent 的意义

后续 `food-agent-service` 或 Python Agent Runtime 可以把这个接口包装成工具：

```text
toolName: querySeckillOrderTrace
input: requestNo 或 orderId
output: 秒杀链路聚合状态
```

Agent 可以回答：

```text
你的秒杀请求已经成功生成订单，现在订单待支付。
活动库存 MySQL 和 Redis 都是 17，当前库存状态一致。
本单套餐原价 16800，秒杀支付金额 9800。
```

## 八、下一步建议

秒杀链路现在已经有：

```text
Redis 预占
请求单
本地消息
异步落单
恢复补偿
库存对账
链路聚合查询
```

下一步建议开始做 Agent 工具注册骨架：

```text
1. 在 food-agent-service 建工具定义表。
2. 注册 trade.querySeckillOrderTrace 工具。
3. Java 侧先提供工具执行接口。
4. 后续 Python Agent Runtime 通过 HTTP 调 Java 工具执行层。
```

# 开发日志 009：trade 服务下单流程设计模式重构

## 一、本次开发目标

本次在 `feature/trade-order-patterns` 分支重构 `trade-service` 普通购买下单流程。

目标：

```text
1. 保持原有普通购买接口不变
2. 将下单校验从 OrderDomainService 拆出去
3. 将金额计算从 OrderDomainService 拆出去
4. 将订单对象创建从 OrderDomainService 拆出去
5. 为后续拼团、秒杀、优惠计算预留扩展点
```

## 二、原来的问题

原来的 `OrderDomainService.createNormalOrder` 同时负责：

```text
1. 校验登录
2. 校验 packageId、quantity
3. 调 business-service 获取套餐快照
4. 校验套餐状态和库存
5. 计算订单金额
6. 生成订单号
7. 创建订单主表
8. 创建订单明细
9. 保存订单
```

短期可用，但继续加入拼团、秒杀、优惠券、会员价之后，方法会越来越胖。

## 三、本次引入的设计模式

### 1. 责任链模式

用途：

```text
承接下单前校验。
```

新增文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/check/OrderCreateCheckHandler.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/check/OrderCreateCheckChain.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/check/OrderCreateCheckStage.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/check/handler/OrderCommandCheckHandler.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/check/handler/PackageTradeCheckHandler.java
```

当前校验分两个阶段：

```text
COMMAND 阶段：
校验用户是否登录、packageId 是否存在、quantity 是否合法。

SNAPSHOT 阶段：
校验套餐是否存在、套餐是否上架、库存是否足够。
```

为什么拆阶段：

```text
因为远程获取套餐快照之前，必须先保证 packageId 合法；
拿到套餐快照之后，才能校验套餐状态和库存。
```

后续扩展：

```text
拼团资格校验
秒杀时间窗口校验
用户限购校验
风控校验
店铺营业时间校验
```

都可以作为新的 Handler 加入责任链。

## 四、策略模式

用途：

```text
承接不同订单类型的金额计算。
```

新增文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/pricing/OrderPricingStrategy.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/pricing/OrderPricingService.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/pricing/NormalOrderPricingStrategy.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/model/OrderPricingResult.java
```

当前普通购买策略：

```text
totalAmount = 套餐价格 * 数量
payAmount = totalAmount
```

后续扩展：

```text
GroupBuyOrderPricingStrategy
SeckillOrderPricingStrategy
CouponOrderPricingStrategy
MemberOrderPricingStrategy
```

后面如果优惠规则继续复杂，可以再从策略模式升级为规则树。

## 五、工厂模式

用途：

```text
承接订单主表和订单明细对象创建。
```

新增文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/factory/OrderFactory.java
```

负责创建：

```text
DiningOrderEntity
DiningOrderItemEntity
```

这样 `OrderDomainService` 不再关心具体字段怎么组装，只负责业务编排。

## 六、订单号生成器

新增文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/sequence/OrderNoGenerator.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/sequence/TimestampOrderNoGenerator.java
```

当前实现：

```text
NO + 当前时间戳 + userId
```

后续可以替换为：

```text
Redis 自增序列
雪花算法
数据库号段
```

## 七、常量抽取

新增文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/constant/TradeTypeConstants.java
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/constant/OrderStatusConstants.java
```

当前包含：

```text
TradeTypeConstants.NORMAL
OrderStatusConstants.WAIT_PAY
```

后续会继续补：

```text
GROUP_BUY
SECKILL
PAID
CANCELED
CLOSED
```

## 八、重构后的主流程

当前 `OrderDomainService.createNormalOrder` 变成：

```text
1. 构建 OrderCreateContext
2. 执行 COMMAND 阶段责任链校验
3. 调用 business-service 获取套餐交易快照
4. 执行 SNAPSHOT 阶段责任链校验
5. 调用价格策略计算订单金额
6. 调用 OrderFactory 创建订单主表
7. 保存订单主表
8. 调用 OrderFactory 创建订单明细
9. 保存订单明细
10. 返回下单结果
```

对应代码：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

## 九、对原接口的影响

接口不变：

```http
POST /api/trade/orders/normal
GET /api/trade/orders/{orderId}
```

请求参数不变。

返回结构不变。

数据库表结构不变。

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

本次没有新增业务接口，而是完成了 trade-service 下单核心流程的结构升级。

当前下单链路已经具备：

```text
1. 责任链校验扩展点
2. 订单类型价格策略扩展点
3. 订单对象工厂
4. 订单号生成器替换点
5. 后续接拼团、秒杀的基础结构
```

## 十二、下一步建议

下一步可以正式开发：

```text
订单取消接口
```

或者继续沿着交易类型扩展：

```text
新增 GROUP_BUY 交易类型骨架
新增 SECKILL 交易类型骨架
```

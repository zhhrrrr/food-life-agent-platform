# 开发日志 010：trade 服务下单模板模式抽象

## 一、本次开发目标

本次在 `feature/trade-order-template` 分支继续重构 `trade-service` 下单流程。

目标：

```text
1. 将下单主流程抽象成模板
2. 让普通购买、拼团、秒杀后续都能复用统一流程
3. 让 OrderDomainService 从流程实现者变成业务入口门面
4. 保持现有普通购买接口不变
```

## 二、为什么需要模板模式

上一版已经拆出了：

```text
责任链
价格策略
订单工厂
订单号生成器
```

但是下单流程本身还写在 `OrderDomainService.createNormalOrder` 里。

这会导致后续做拼团、秒杀时容易出现：

```text
createNormalOrder
createGroupBuyOrder
createSeckillOrder
```

三个方法里重复写相似流程。

所以这次把主流程抽象成模板。

## 三、新增模板结构

新增目录：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/create
```

新增文件：

```text
OrderCreateTemplate.java
AbstractOrderCreateTemplate.java
NormalOrderCreateTemplate.java
OrderCreateTemplateRouter.java
```

## 四、模板接口

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/create/OrderCreateTemplate.java
```

职责：

```text
定义所有下单模板必须支持的两个方法。
```

方法：

```text
support(String tradeType)
create(CreateOrderCommand command)
```

含义：

```text
support 用来判断这个模板支持哪种交易类型。
create 用来执行下单。
```

## 五、抽象模板

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/create/AbstractOrderCreateTemplate.java
```

职责：

```text
固定所有订单类型共用的下单主流程。
```

模板流程：

```text
1. 构建 OrderCreateContext
2. 执行 beforeCommandCheck 扩展点
3. 执行 COMMAND 阶段责任链校验
4. 加载套餐交易快照
5. 执行 beforeSnapshotCheck 扩展点
6. 执行 SNAPSHOT 阶段责任链校验
7. 执行 beforePricing 扩展点
8. 调用价格策略计算金额
9. 执行 beforeCreateOrder 扩展点
10. 调用 OrderFactory 创建订单主表
11. 保存订单主表
12. 调用 OrderFactory 创建订单明细
13. 保存订单明细
14. 执行 afterOrderSaved 扩展点
15. 构建 CreateOrderResult 返回
```

模板里固定的能力：

```text
责任链
价格策略
订单工厂
订单仓储
```

模板里留出的扩展点：

```text
beforeCommandCheck
beforeSnapshotCheck
beforePricing
beforeCreateOrder
afterOrderSaved
loadPackageSnapshot
getTradeType
```

其中：

```text
getTradeType 和 loadPackageSnapshot 是子类必须实现的差异点。
```

## 六、普通购买模板

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/create/NormalOrderCreateTemplate.java
```

职责：

```text
实现普通购买订单的差异点。
```

当前普通购买只需要：

```text
1. 声明支持 TradeTypeConstants.NORMAL
2. 通过 business-service 加载套餐交易快照
```

也就是说，普通购买模板不再重复写保存订单、创建明细、返回结果等通用流程。

## 七、模板路由器

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/create/OrderCreateTemplateRouter.java
```

职责：

```text
根据 tradeType 找到对应下单模板。
```

当前调用：

```text
NORMAL -> NormalOrderCreateTemplate
```

后续扩展：

```text
GROUP_BUY -> GroupBuyOrderCreateTemplate
SECKILL -> SeckillOrderCreateTemplate
```

## 八、OrderDomainService 变化

文件：

```text
food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
```

现在普通购买下单变成：

```text
orderCreateTemplateRouter.create(TradeTypeConstants.NORMAL, command)
```

`OrderDomainService` 不再直接关心：

```text
责任链怎么执行
套餐快照怎么装入上下文
价格怎么算
订单对象怎么创建
订单明细怎么创建
结果怎么返回
```

这些都交给模板处理。

## 九、后续业务如何调用

### 普通购买

```text
tradeType = NORMAL
OrderCreateTemplateRouter 找到 NormalOrderCreateTemplate
执行 AbstractOrderCreateTemplate 固定流程
```

### 拼团购买

后续新增：

```text
GroupBuyOrderCreateTemplate
GroupBuyOrderPricingStrategy
GroupBuyCheckHandler
```

调用方式：

```text
orderCreateTemplateRouter.create(TradeTypeConstants.GROUP_BUY, command)
```

### 秒杀购买

后续新增：

```text
SeckillOrderCreateTemplate
SeckillOrderPricingStrategy
SeckillTimeCheckHandler
SeckillLimitBuyCheckHandler
```

调用方式：

```text
orderCreateTemplateRouter.create(TradeTypeConstants.SECKILL, command)
```

## 十、本次接口影响

接口不变：

```http
POST /api/trade/orders/normal
GET /api/trade/orders/{orderId}
```

请求结构不变。

返回结构不变。

数据库结构不变。

## 十一、验证

执行：

```bash
mvn -q test
```

结果：

```text
通过。
```

## 十二、本次结论

现在下单流程已经真正形成模板：

```text
OrderDomainService
  ↓
OrderCreateTemplateRouter
  ↓
具体 OrderCreateTemplate
  ↓
AbstractOrderCreateTemplate 固定主流程
  ↓
责任链、价格策略、订单工厂、仓储
```

后续新增订单类型时，不需要复制普通购买流程，只需要新增对应模板和差异组件。

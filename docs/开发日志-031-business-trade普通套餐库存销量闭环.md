# 开发日志 031：普通套餐库存销量闭环

## 一、开发目标

本次先不做 Agent，只补齐不涉及 Agent 的业务主流程。

本次目标是把普通购买从“只生成订单数据”推进到“订单状态变化会影响基础套餐库存和销量”：

```text
普通下单成功：meal_package.stock 扣减
普通未支付取消：meal_package.stock 释放
普通支付成功：meal_package.sold 增加，shop.sold 增加
普通支付后退款：meal_package.sold 回滚，shop.sold 回滚，meal_package.stock 释放
```

拼团和秒杀暂时不复用这套基础套餐库存扣减逻辑，它们仍然使用各自的活动库存模型。

## 二、涉及服务

### 1. food-business-service

业务服务维护美食店铺域的数据，本次新增套餐库存和销量变更能力。

相关文件：

```text
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/packagee/model/PackageStockChangeResult.java
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/packagee/repository/IPackageRepository.java
food-business-service/food-business-domain/src/main/java/com/foodlife/business/domain/packagee/service/PackageDomainService.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/repository/PackageRepository.java
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/http/PackageController.java
```

新增业务接口：

```http
POST /api/package/{packageId}/stock/occupy?quantity=1
POST /api/package/{packageId}/stock/release?quantity=1
POST /api/package/{packageId}/sold/confirm?quantity=1
POST /api/package/{packageId}/sold/rollback?quantity=1
```

### 2. food-trade-service

交易服务在普通订单生命周期里调用业务服务，不直接改业务库。

相关文件：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/port/IBusinessPackagePort.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/port/BusinessPackagePort.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/OrderDomainService.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/service/settlement/OrderPaySettlementService.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/refund/filter/RefundOrderRuleFilter.java
```

## 三、普通购买业务流

### 1. 普通下单

```text
1. 校验登录用户、packageId、quantity
2. 通过 business-service 获取套餐交易快照
3. 计算订单金额
4. 调用 business-service 预占套餐库存
5. 生成订单号
6. 保存 dining_order 主表
7. 保存 dining_order_item 明细快照
8. 返回 WAIT_PAY 订单
```

如果预占库存成功但订单保存失败，trade-service 会调用 business-service 释放刚刚预占的库存。

### 2. 普通订单取消

```text
1. 校验订单属于当前用户
2. 校验订单状态必须是 WAIT_PAY
3. 更新 dining_order 为 CANCELED
4. 调用 business-service 释放套餐库存
```

### 3. 普通订单支付

```text
1. 校验支付来源、渠道、外部交易号
2. 校验订单属于当前用户
3. 校验订单状态必须是 WAIT_PAY
4. 更新 dining_order 为 PAID
5. 调用 business-service 确认销量
```

确认销量会同时增加：

```text
meal_package.sold
shop.sold
```

### 4. 普通订单退款

```text
1. 校验退款来源、渠道、订单归属
2. 校验订单状态必须是 PAID
3. 更新 dining_order 为 REFUNDED
4. 调用 business-service 回滚套餐销量
5. 调用 business-service 释放套餐库存
```

退款后，基础库存回到可售库存，销量同步减少。

## 四、DDD 分层说明

本次仍然按 DDD 结构放置代码：

```text
domain
承载业务动作定义、领域服务、仓储接口。

infrastructure
实现数据库更新、跨服务 HTTP 调用。

trigger
暴露 HTTP 接口。
```

trade-service 通过 `IBusinessPackagePort` 依赖业务能力，不直接依赖 business-service 的实现细节。

## 五、当前一致性说明

当前是本地开发阶段，普通订单状态和套餐库存销量分属两个微服务数据库：

```text
food_trade_db
food_business_db
```

因此这一步先使用同步 HTTP 调用完成闭环，暂时不引入分布式事务。

当前风险：

```text
订单状态更新成功后，如果后续跨服务库存/销量调用失败，可能出现短暂不一致。
```

后续生产化建议：

```text
引入本地消息表 / Outbox
增加订单库存补偿任务
将库存销量变更动作做幂等号
必要时再接 MQ
```

这和前面秒杀异步请求单、local message 的方向是一致的。

## 六、验证记录

已执行：

```bash
mvn -pl food-business-service/food-business-app,food-trade-service/food-trade-app -am test
```

结果：

```text
BUILD SUCCESS
```

已执行打包：

```bash
mvn -pl food-business-service/food-business-app,food-trade-service/food-trade-app -am package -DskipTests
```

结果：

```text
BUILD SUCCESS
```

已执行本地接口验证。

### 1. 普通下单后取消

验证订单：

```text
order_id = 41
```

数据变化：

```text
下单前：
meal_package.stock = 100
meal_package.sold = 52

下单后：
meal_package.stock = 99
meal_package.sold = 52
dining_order.order_status = WAIT_PAY

取消后：
meal_package.stock = 100
meal_package.sold = 52
dining_order.order_status = CANCELED
```

### 2. 普通下单后支付再退款

验证订单：

```text
order_id = 42
```

数据变化：

```text
下单前：
meal_package.stock = 100
meal_package.sold = 52
shop.sold = 318

下单后：
meal_package.stock = 99
meal_package.sold = 52
shop.sold = 318
dining_order.order_status = WAIT_PAY

支付后：
meal_package.stock = 99
meal_package.sold = 53
shop.sold = 319
dining_order.order_status = PAID

退款后：
meal_package.stock = 100
meal_package.sold = 52
shop.sold = 318
dining_order.order_status = REFUNDED
```

结论：

```text
普通购买库存预占、未支付取消释放、支付确认销量、退款回滚库存销量均已跑通。
```

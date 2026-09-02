# 开发日志-062-Sentinel限流熔断降级

## 本次目标

第四阶段进入 Sentinel 服务治理，把高并发场景从“自己写固定窗口限流”升级为更主流的 Spring Cloud Alibaba Sentinel 方案。

本次覆盖：

- Gateway 入口级路由限流、API 分组限流、用户请求头限流
- 普通下单接口限流
- 拼团锁单接口限流
- 秒杀下单接口限流
- 支付回调接口限流
- 套餐库存扣减接口热点参数限流
- OpenFeign 远程调用熔断降级 fallback

## 为什么替换 Gateway 自研限流

上一阶段 Gateway 做了 Redis 固定窗口限流，用来补齐入口防护能力是可以的。

但如果目标是贴合生产环境和大厂面试，Sentinel 更适合继续作为主方案：

- 支持 QPS、并发线程数、热点参数、熔断降级等完整治理能力
- 支持 Gateway Adapter，对路由和 API 分组直接限流
- 支持 OpenFeign fallback，远程服务异常时能快速降级
- 规则模型更贴近生产环境，也更容易讲清楚高并发治理

因此本次保留旧的 `GatewayTrafficGuardFilter` 黑名单能力，但把其中 Redis 限流默认关闭。限流主方案切换到 Sentinel。

## 新增与修改文件

### 1. Gateway Sentinel 依赖

```text
food-gateway-service/pom.xml
```

新增：

```text
spring-cloud-starter-alibaba-sentinel
spring-cloud-alibaba-sentinel-gateway
```

作用：

- 让 Gateway 接入 Sentinel 客户端
- 让 Gateway 支持基于 routeId、API 分组、请求参数的限流

### 2. Gateway Sentinel 配置属性

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySentinelProperties.java
```

新增配置前缀：

```yaml
food:
  gateway:
    sentinel:
```

当前支持配置：

```text
enabled
trade-route-qps
business-route-qps
user-route-qps
trade-order-create-qps
seckill-order-create-qps
payment-callback-qps
user-header-name
user-header-qps
smoke-rule-enabled
```

### 3. Gateway Sentinel 规则注册

```text
food-gateway-service/src/main/java/com/foodlife/gateway/config/GatewaySentinelRuleConfiguration.java
```

启动时注册三类规则：

```text
routeId 维度：
food-user-route
food-business-route
food-trade-route

API 分组维度：
gateway_api_trade_order_create
gateway_api_seckill_order_create
gateway_api_payment_callback
gateway_api_sentinel_smoke

请求头参数维度：
authorization
X-Sentinel-Smoke
```

命中 Sentinel 限流后，Gateway 统一返回：

```json
{"code":"429","message":"service busy, please try again later"}
```

### 4. trade-service Sentinel 依赖

```text
food-trade-service/food-trade-trigger/pom.xml
```

新增：

```text
spring-cloud-starter-alibaba-sentinel
```

### 5. trade-service Sentinel 资源名

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/sentinel/TradeSentinelResources.java
```

统一维护 Sentinel 资源名，避免各 Controller 到处写字符串。

当前资源：

```text
trade.order.normal.create
trade.order.groupBuy.create
trade.order.seckill.create
trade.order.seckill.asyncCreate
trade.order.user.create
trade.seckill.stock.occupy
trade.payment.callback
```

### 6. trade-service Sentinel 规则

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/config/TradeSentinelRuleConfiguration.java
```

启动时加载：

```text
普通下单 QPS：60
拼团锁单 QPS：60
秒杀同步下单 QPS：20
秒杀异步下单 QPS：40
支付回调 QPS：100
用户维度下单限流：同一 userId 60 秒 5 次
秒杀库存热点参数限流：同一 activityId 每秒 20 次
```

这里的 `SentinelResourceAspect` 是 `@SentinelResource` 生效的关键。

### 7. 普通、拼团、秒杀下单限流

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
```

实现方式：

- 普通下单方法使用 `@SentinelResource`
- 拼团锁单方法使用 `@SentinelResource`
- 秒杀下单方法使用 `@SentinelResource`
- 每个下单入口再通过 `SphU.entry(trade.order.user.create, userId)` 做用户维度限流
- 秒杀下单进入业务前，再通过 `SphU.entry(trade.seckill.stock.occupy, activityId)` 做热点参数保护

这样命中限流时会快速返回 `429`，不会继续打到数据库。

### 8. 支付回调限流

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/PaymentController.java
```

支付回调入口加 `@SentinelResource(trade.payment.callback)`。

命中限流时返回：

```json
{"code":"429","message":"payment callback service busy, please try again later"}
```

### 9. business-service Sentinel 依赖

```text
food-business-service/food-business-trigger/pom.xml
```

新增：

```text
spring-cloud-starter-alibaba-sentinel
```

### 10. business-service Sentinel 资源名

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/sentinel/BusinessSentinelResources.java
```

当前资源：

```text
business.package.stock.occupy
business.package.stock.release
business.package.sold.confirm
business.package.sold.rollback
```

### 11. 套餐库存热点参数限流

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/config/BusinessSentinelRuleConfiguration.java
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/http/PackageController.java
```

套餐库存操作基于 `packageId` 做热点参数限流：

```text
占用库存：同一 packageId 每秒 20 次
释放库存：同一 packageId 每秒 40 次
确认销量：同一 packageId 每秒 40 次
回滚销量：同一 packageId 每秒 40 次
```

命中限流直接返回：

```json
{"code":"429","message":"package stock service busy, please try again later"}
```

### 12. trade -> business Feign 降级

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClient.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClientFallback.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/port/BusinessPackagePort.java
```

套餐快照查询失败时，fallback 返回：

```json
{"code":"503","message":"服务繁忙，请稍后再试"}
```

端口层识别 `503` 后抛出业务可理解的 `IllegalStateException`，Controller 统一转成：

```json
{"code":"503","message":"服务繁忙，请稍后再试"}
```

### 13. business -> trade Feign 降级

```text
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClient.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClientFallback.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/port/TradeOrderPort.java
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/http/ShopReviewController.java
```

评价创建时需要校验订单，如果订单服务不可用，fallback 返回：

```json
{"code":"503","message":"订单服务暂不可用"}
```

Controller 捕获后返回给前端，避免远程异常穿透成 500。

## 配置变更

### local 配置

```text
food-gateway-service/src/main/resources/application-local.yml
food-business-service/food-business-app/src/main/resources/application-local.yml
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

本地模式下也开启 Sentinel，并为每个服务指定不同 transport 端口：

```text
gateway: 8719
business: 8720
trade: 8721
```

### Nacos 配置

```text
deploy/nacos/configs/food-common.yaml
deploy/nacos/configs/food-gateway-service.yaml
deploy/nacos/configs/food-business-service.yaml
deploy/nacos/configs/food-trade-service.yaml
```

Nacos 模式下：

- `food-common.yaml` 统一开启 Feign Sentinel
- 各服务自己的 yaml 配置自己的 Sentinel transport 端口
- Gateway 的 Sentinel 路由/API/用户限流阈值放在 `food-gateway-service.yaml`

## 验证记录

全量测试：

```powershell
mvn test
```

结果：通过。

Nacos 配置发布与读取：

```powershell
scripts/publish-nacos-configs.ps1
scripts/verify-nacos-configs.ps1
```

结果：通过。

Nacos 模式启动与服务注册：

```powershell
scripts/start-local-services-nacos.ps1 -Restart
scripts/verify-nacos-services.ps1
```

结果：四个服务全部健康。

Gateway 基础冒烟：

```powershell
scripts/smoke-gateway.ps1
```

结果：通过。

Gateway Sentinel 冒烟：

```powershell
scripts/smoke-gateway-traffic-guards.ps1
```

结果：

```text
黑名单 -> 403
第一次 Sentinel smoke 请求 -> 200
第二次相同 X-Sentinel-Smoke 请求 -> 429
```

服务内 Sentinel 冒烟：

```powershell
scripts/smoke-sentinel-rules.ps1
```

结果：

```text
登录成功
用户下单维度限流 -> 第 6 次返回 429
套餐 packageId 热点参数限流 -> 第 21 次返回 429
```

Feign 降级验证：

```text
停止 business-service 后创建普通订单
```

结果：trade-service 返回 `503 服务繁忙，请稍后再试`。

```text
停止 trade-service 后创建评价
```

结果：business-service 返回 `503 订单服务暂不可用`。

## 当前完成状态

```text
1. 秒杀下单接口 QPS 限流                 已完成
2. 普通下单接口 QPS 限流                 已完成
3. 拼团锁单接口 QPS 限流                 已完成
4. 支付回调接口 QPS 限流                 已完成
5. 套餐库存扣减 packageId 热点参数限流   已完成
6. Feign 远程调用熔断降级 fallback       已完成
7. Gateway 自研限流切换为 Sentinel 主方案 已完成
```

## 面试表达

这一阶段可以这样讲：

```text
项目里有秒杀、拼团、普通下单和支付回调，所以我没有只在网关写一个简单限流，而是分层治理。
Gateway 负责入口流量保护，使用 Sentinel Gateway Adapter 按 routeId 和 API 分组做限流。
trade-service 在业务入口使用 @SentinelResource 做普通下单、拼团锁单、秒杀下单、支付回调的资源保护，同时用 SphU.entry 手动传入 userId，实现用户维度限流。
business-service 对套餐库存接口使用热点参数限流，按 packageId 保护库存扣减入口。
跨服务调用上开启 feign.sentinel.enabled，并给 Feign Client 增加 fallback，套餐快照失败返回服务繁忙，评价查询订单失败返回订单服务暂不可用。
这样既有入口限流，也有服务内热点保护，还有远程调用降级，能避免高并发下流量直接打穿数据库或把异常扩散到调用方。
```


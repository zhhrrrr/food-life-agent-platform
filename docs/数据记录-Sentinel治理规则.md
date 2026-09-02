# 数据记录-Sentinel治理规则

## 方案定位

Sentinel 是当前微服务高并发治理的主方案。

上一阶段 Gateway 的 Redis 固定窗口限流仍保留代码，但默认关闭；Gateway 黑名单仍继续生效。

当前治理边界：

```text
Gateway:
入口路由限流、API 分组限流、请求头用户维度限流

trade-service:
普通下单、拼团锁单、秒杀下单、支付回调、用户维度下单限流、秒杀热点库存限流

business-service:
套餐库存 packageId 热点参数限流

OpenFeign:
远程调用失败时 fallback 降级
```

## 配置位置

local 兜底配置：

```text
food-gateway-service/src/main/resources/application-local.yml
food-business-service/food-business-app/src/main/resources/application-local.yml
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

Nacos 配置中心基线：

```text
deploy/nacos/configs/food-common.yaml
deploy/nacos/configs/food-gateway-service.yaml
deploy/nacos/configs/food-business-service.yaml
deploy/nacos/configs/food-trade-service.yaml
```

## Sentinel Dashboard 地址

当前配置：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD_ADDR:localhost:8858}
```

说明：

```text
项目已配置客户端上报地址。
如果本地没有启动 Sentinel Dashboard，业务服务仍可运行，规则也会通过代码初始化生效。
后续启动 Dashboard 后，可在控制台观察资源、QPS、限流命中情况。
```

## Sentinel Transport 端口

每个服务必须使用不同端口，避免本机多服务启动时端口冲突。

```text
food-gateway-service  8719
food-business-service 8720
food-trade-service    8721
```

## Feign Sentinel 开关

公共 Nacos 配置：

```yaml
feign:
  sentinel:
    enabled: true
```

作用：

```text
开启后，OpenFeign 调用可以接入 Sentinel，并使用 @FeignClient(fallback = xxx.class) 的降级实现。
```

## Gateway Sentinel 规则

配置类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/config/GatewaySentinelRuleConfiguration.java
```

配置属性：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySentinelProperties.java
```

当前默认规则：

```text
food-trade-route     QPS 300
food-business-route  QPS 500
food-user-route      QPS 200
```

API 分组规则：

```text
gateway_api_trade_order_create    QPS 80
gateway_api_seckill_order_create  QPS 30
gateway_api_payment_callback      QPS 100
gateway_api_sentinel_smoke        X-Sentinel-Smoke 60 秒 1 次
```

用户请求头规则：

```text
API 分组：gateway_api_trade_order_create
参数来源：Header authorization
阈值：同一 authorization 每秒 20 次
```

命中返回：

```json
{"code":"429","message":"service busy, please try again later"}
```

## trade-service Sentinel 规则

资源名：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/sentinel/TradeSentinelResources.java
```

规则注册：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/config/TradeSentinelRuleConfiguration.java
```

Controller 入口：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OrderController.java
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/PaymentController.java
```

当前规则：

```text
trade.order.normal.create          QPS 60
trade.order.groupBuy.create        QPS 60
trade.order.seckill.create         QPS 20
trade.order.seckill.asyncCreate    QPS 40
trade.payment.callback             QPS 100
trade.order.user.create            userId 60 秒 5 次
trade.seckill.stock.occupy         activityId 每秒 20 次
```

说明：

```text
接口级 QPS 用 @SentinelResource。
用户维度、activityId 热点参数用 SphU.entry 手动传参。
这样 Sentinel 能拿到 userId、activityId 这种业务参数。
```

## business-service Sentinel 规则

资源名：

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/sentinel/BusinessSentinelResources.java
```

规则注册：

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/config/BusinessSentinelRuleConfiguration.java
```

Controller 入口：

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/http/PackageController.java
```

当前规则：

```text
business.package.stock.occupy    packageId 每秒 20 次
business.package.stock.release   packageId 每秒 40 次
business.package.sold.confirm    packageId 每秒 40 次
business.package.sold.rollback   packageId 每秒 40 次
```

命中返回：

```json
{"code":"429","message":"package stock service busy, please try again later"}
```

## OpenFeign 降级规则

trade 调 business：

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClient.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClientFallback.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/port/BusinessPackagePort.java
```

降级语义：

```text
套餐快照查询失败 -> 服务繁忙，请稍后再试
库存占用、释放、确认、回滚失败 -> 对应库存服务暂不可用
```

business 调 trade：

```text
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClient.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClientFallback.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/port/TradeOrderPort.java
```

降级语义：

```text
评价查询订单失败 -> 订单服务暂不可用
```

## 和旧 Gateway Redis 限流的关系

旧代码位置：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayTrafficGuardFilter.java
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySecurityProperties.java
```

当前状态：

```text
黑名单能力继续使用
Redis IP 限流默认关闭
Redis 用户限流默认关闭
Sentinel Gateway 成为默认入口限流方案
```

配置：

```yaml
food:
  gateway:
    security:
      rate-limit:
        enabled: false
```

## 验证脚本

Gateway Sentinel：

```text
scripts/smoke-gateway-traffic-guards.ps1
```

验证：

```text
黑名单 403
Sentinel Gateway 第一次通过
Sentinel Gateway 第二次限流
```

服务内 Sentinel：

```text
scripts/smoke-sentinel-rules.ps1
```

验证：

```text
用户下单维度限流
套餐 packageId 热点参数限流
```


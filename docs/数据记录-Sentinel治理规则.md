# 数据记录-Sentinel治理规则

## 方案定位

Sentinel 是当前项目微服务高并发治理的主方案。

它负责：

```text
Gateway:
  路由限流
  API 分组限流
  Header 参数限流

trade-service:
  普通下单限流
  拼团锁单限流
  秒杀下单限流
  支付回调限流
  userId 用户维度限流
  activityId 秒杀热点参数限流

business-service:
  packageId 套餐库存热点参数限流

OpenFeign:
  远程调用熔断降级
```

## 配置位置

local 兜底配置：

```text
food-gateway-service/src/main/resources/application-local.yml
food-business-service/food-business-app/src/main/resources/application-local.yml
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

Nacos 配置：

```text
deploy/nacos/configs/food-common.yaml
deploy/nacos/configs/food-gateway-service.yaml
deploy/nacos/configs/food-business-service.yaml
deploy/nacos/configs/food-trade-service.yaml
```

## Sentinel Dashboard

地址：

```text
http://127.0.0.1:8858
```

服务上报配置：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD_ADDR:localhost:8858}
```

本地 transport 端口：

```text
food-gateway-service   8719
food-business-service  8720
food-trade-service     8721
```

## Gateway 规则

规则注册类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/config/GatewaySentinelRuleConfiguration.java
```

属性类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySentinelProperties.java
```

路由级规则：

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
gateway_api_sentinel_smoke        Header X-Sentinel-Smoke 60 秒 1 次
```

用户维度入口限流：

```text
API 分组: gateway_api_trade_order_create
参数来源: Header authorization
阈值: 同一 authorization 每秒 20 次
```

命中返回：

```json
{"code":"429","message":"service busy, please try again later"}
```

## trade-service 规则

资源名：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/sentinel/TradeSentinelResources.java
```

规则注册：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/config/TradeSentinelRuleConfiguration.java
```

入口 Controller：

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

## business-service 规则

资源名：

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/sentinel/BusinessSentinelResources.java
```

规则注册：

```text
food-business-service/food-business-trigger/src/main/java/com/foodlife/business/trigger/config/BusinessSentinelRuleConfiguration.java
```

入口 Controller：

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

## OpenFeign 熔断降级

trade 调 business：

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClient.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/feign/BusinessPackageClientFallback.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/port/BusinessPackagePort.java
```

business 调 trade：

```text
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClient.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/feign/TradeOrderClientFallback.java
food-business-service/food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/port/TradeOrderPort.java
```

降级语义：

```text
套餐快照查询失败:
  返回服务繁忙，请稍后再试

库存占用、释放、确认、回滚失败:
  返回库存服务暂不可用

评价查询订单失败:
  返回订单服务暂不可用
```

## 验证脚本

Gateway：

```text
scripts/smoke-gateway-traffic-guards.ps1
```

服务内 Sentinel：

```text
scripts/smoke-sentinel-rules.ps1
```

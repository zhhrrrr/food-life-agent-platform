# 数据记录-Gateway安全配置

## 当前结论

Gateway 只保留横切安全能力，不再维护自研 Redis 固定窗口限流。

当前职责边界：

```text
GatewayBlacklistFilter:
  黑名单路径拦截
  公网入口屏蔽 /internal/** 和 /actuator/**

GatewayAuthFilter:
  用户 token 校验
  用户上下文请求头透传

GatewaySentinelRuleConfiguration:
  路由级限流
  API 分组限流
  Header 参数限流
  统一 429 JSON 响应
```

## 配置位置

local 兜底配置：

```text
food-gateway-service/src/main/resources/application-local.yml
```

Nacos 配置：

```text
deploy/nacos/configs/food-gateway-service.yaml
```

安全属性类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySecurityProperties.java
```

Sentinel 属性类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySentinelProperties.java
```

## 黑名单配置

配置前缀：

```yaml
food:
  gateway:
    security:
      blacklist:
        enabled: true
        paths:
          - /internal/**
          - /actuator/**
```

作用：

```text
/internal/**:
  内部接口不能从公网统一入口直接访问。

/actuator/**:
  Gateway 业务端口 8080 不暴露健康检查和监控端点。
  管理端口 8081 允许访问，用于 Prometheus、Grafana 和本地健康检查。
```

命中后返回：

```json
{"code":"403","message":"forbidden"}
```

## Token 透传

Gateway 支持请求头：

```text
authorization: {token}
Authorization: Bearer {token}
```

Gateway 校验通过后，转发给下游：

```text
authorization: {token}
Authorization: {token}
x-user-id: {userId}
x-user-nick-name: {nickName}
```

这样可以兼容黑马点评 Redis Token 登录方式，也能让下游服务继续做 auth-starter 二次校验。

## 限流配置

当前入口限流归 Sentinel 统一管理，不再放在 `food.gateway.security.rate-limit`。

Gateway 当前 Sentinel 配置：

```yaml
food:
  gateway:
    sentinel:
      enabled: true
      trade-route-qps: 300
      business-route-qps: 500
      user-route-qps: 200
      trade-order-create-qps: 80
      seckill-order-create-qps: 30
      payment-callback-qps: 100
      user-header-name: authorization
      user-header-qps: 20
      smoke-rule-enabled: true
```

核心含义：

```text
route-qps:
  控制某个下游服务整体入口流量。

API qps:
  控制下单、秒杀、支付回调等关键入口。

user-header-qps:
  基于 authorization 请求头做用户维度入口限流。
```

命中后返回：

```json
{"code":"429","message":"service busy, please try again later"}
```

## 验证脚本

```text
scripts/smoke-gateway-traffic-guards.ps1
```

验证内容：

```text
1. /internal/** 通过 8080 访问返回 403
2. /actuator/** 通过 8080 访问返回 403
3. Sentinel smoke API 第一次通过
4. Sentinel smoke API 同 Header 第二次返回 429
```

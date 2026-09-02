# 数据记录-Gateway安全配置

## 当前状态

这份文档记录的是上一阶段的 Gateway 自研 Redis 固定窗口限流方案。

第四阶段接入 Sentinel 后，当前主流量治理方案已经切换为 Sentinel：

```text
docs/数据记录-Sentinel治理规则.md
```

当前代码状态：

```text
Gateway 黑名单继续使用
Redis IP 限流默认关闭
Redis 用户维度限流默认关闭
Gateway 入口限流默认由 Sentinel Gateway Adapter 承担
```

当前配置：

```yaml
food:
  gateway:
    security:
      rate-limit:
        enabled: false
```

## 配置位置

local 兜底配置：

```text
food-gateway-service/src/main/resources/application-local.yml
```

Nacos 配置中心配置：

```text
deploy/nacos/configs/food-gateway-service.yaml
```

## 黑名单配置

配置前缀：

```yaml
food:
  gateway:
    security:
      blacklist:
```

当前配置：

```yaml
enabled: true
paths:
  - /internal/**
  - /actuator/**
```

含义：

- `/internal/**`：内部接口不允许从公网统一入口直接访问
- `/actuator/**`：监控端点不直接暴露给用户入口

命中后返回：

```json
{"code":"403","message":"forbidden"}
```

## IP 限流配置

配置前缀：

```yaml
food:
  gateway:
    security:
      rate-limit:
        ip:
```

当前配置：

```yaml
enabled: true
capacity: 120
window-seconds: 60
```

含义：

```text
同一个 IP 在 60 秒窗口内最多允许 120 次请求。
```

Redis key：

```text
food:gateway:rate-limit:ip:{sha256(clientIp)}:{windowBucket}
```

示例：

```text
food:gateway:rate-limit:ip:8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4:29801489
```

命中后返回：

```json
{"code":"429","message":"too many requests"}
```

## 用户维度限流配置

配置前缀：

```yaml
food:
  gateway:
    security:
      rate-limit:
        user:
```

当前配置：

```yaml
enabled: true
capacity: 60
window-seconds: 60
```

含义：

```text
同一个登录 token 在 60 秒窗口内最多允许 60 次请求。
```

Redis key：

```text
food:gateway:rate-limit:user:{sha256(token)}:{windowBucket}
```

这里不直接把 token 明文放进限流 key，而是使用 SHA-256 后的摘要，避免在 Redis key 列表里暴露登录 token。

## 客户端 IP 识别规则

优先级：

```text
1. X-Forwarded-For 的第一个 IP
2. X-Real-IP
3. remoteAddress
4. unknown
```

生产环境里如果 Gateway 前面还有 Nginx、SLB、Ingress，需要保证它们正确传递 `X-Forwarded-For` 或 `X-Real-IP`。

## Token 透传规则

Gateway 支持两种请求头：

```text
authorization: {token}
Authorization: Bearer {token}
```

经过 Gateway 鉴权后，都会归一化成原始 token 转发给下游：

```text
authorization: {token}
Authorization: {token}
```

这样可以兼容黑马点评登录拦截器的实现方式。

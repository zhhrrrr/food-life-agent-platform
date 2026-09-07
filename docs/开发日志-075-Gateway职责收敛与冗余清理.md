# 开发日志-075-Gateway 职责收敛与冗余清理

## 1. 本次目标

本次检查 Gateway 中是否存在重复功能、冲突功能和容易误导后续开发的旧实现。

结论：

- Gateway 黑名单能力保留。
- Gateway Redis 自研固定窗口限流移除。
- Gateway 入口限流统一交给 Sentinel Gateway Adapter。
- Gateway 业务端口禁止访问 `/actuator/**`。
- Gateway 管理端口继续允许 Prometheus 采集 `/actuator/prometheus`。

## 2. 为什么移除 Redis 自研限流

旧实现位置：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayTrafficGuardFilter.java
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySecurityProperties.java
```

旧逻辑同时承担：

- 黑名单拦截
- IP 维度 Redis 固定窗口限流
- token 维度 Redis 固定窗口限流

但项目已经引入 Sentinel Gateway：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/config/GatewaySentinelRuleConfiguration.java
```

如果两套限流同时存在，会带来几个问题：

- 同一请求可能同时被 Redis 限流和 Sentinel 限流拦截。
- 两边阈值可能不一致，排查线上限流原因会变复杂。
- Redis 固定窗口限流能力弱于 Sentinel 的网关规则、API 分组和热点参数能力。
- 面试表达上也不清晰，不知道项目主流量治理到底归谁。

所以本次把 Gateway 的流量治理统一收敛到 Sentinel。

## 3. 新的 Gateway 职责划分

| 能力 | 当前承载位置 | 说明 |
| --- | --- | --- |
| 统一入口 | Spring Cloud Gateway 路由配置 | 只负责入口和路由转发 |
| CORS | `spring.cloud.gateway.globalcors` | 统一跨域 |
| Token 校验 | `GatewayAuthFilter` | 查 Redis Token，解析用户信息 |
| Token 透传 | `GatewayAuthFilter` | 向下游透传 `authorization` 和用户基础 header |
| 内部 header 清理 | `GatewayAuthFilter` | 防止外部伪造 `x-internal-call`、`x-internal-secret` |
| 黑名单 | `GatewayBlacklistFilter` | 阻断公网访问内部路径、库存写路径和业务端口 actuator |
| API/路由限流 | `GatewaySentinelRuleConfiguration` | Sentinel Gateway 主方案 |
| traceId | `ReactiveTraceWebFilter` | 可观测性 starter 统一生成和传递 |
| 请求日志/慢接口 | `ReactiveTraceWebFilter` | 不放在 Gateway 业务过滤器里 |

## 4. 代码变动

### 4.1 GatewayTrafficGuardFilter 改为 GatewayBlacklistFilter

新文件：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayBlacklistFilter.java
```

移除内容：

- `ReactiveStringRedisTemplate`
- `GatewayAuthProperties`
- `RATE_LIMIT_PREFIX`
- IP 解析
- token 读取
- SHA-256 hash
- Redis increment + expire 固定窗口限流
- `429 too many requests` 本地响应

保留内容：

- `403 forbidden` 黑名单响应
- `AntPathMatcher` 路径匹配
- OPTIONS 预检请求放行

### 4.2 GatewaySecurityProperties 只保留黑名单

位置：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySecurityProperties.java
```

移除：

```text
RateLimit
Limit
```

现在该配置类只表达安全黑名单，职责更清晰。

### 4.3 移除 YAML 中的 food.gateway.security.rate-limit

位置：

```text
food-gateway-service/src/main/resources/application-local.yml
deploy/nacos/configs/food-gateway-service.yaml
```

移除旧配置：

```yaml
food:
  gateway:
    security:
      rate-limit:
```

限流统一走：

```yaml
food:
  gateway:
    sentinel:
```

### 4.4 Sentinel 配置清理

位置：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/config/GatewaySentinelRuleConfiguration.java
```

优化点：

- 去掉重复 `BlockRequestHandler` Bean。
- 只保留 `GatewayCallbackManager.setBlockHandler(...)` 一个生效入口。
- 提取 `apiQpsRule`、`routeQpsRule`、`headerQpsRule`，减少重复构造代码。
- 清理历史乱码注释。

### 4.5 Gateway 用户维度限流 header 统一

原配置：

```yaml
user-header-name: x-user-id
```

新配置：

```yaml
user-header-name: authorization
```

原因：

Gateway 层的 Sentinel 过滤器和自定义鉴权过滤器存在执行顺序问题。`x-user-id` 是 Gateway 鉴权后才补充给下游的 header，如果 Sentinel 先执行，可能取不到。

所以 Gateway 层使用原始请求天然携带的 `authorization` 做用户请求维度限流。

真正基于 `userId` 的业务限流继续留在 trade-service：

```text
trade-service OrderController / Seckill 入口
SphU.entry(...)
```

## 5. actuator 访问规则修正

旧配置中黑名单包含：

```yaml
- /actuator/**
```

但旧过滤器代码又直接放行所有 `/actuator`，存在配置和代码语义不一致。

新规则：

- 请求打到 Gateway 业务端口 `8080` 的 `/actuator/**`：返回 `403`。
- 请求打到 Gateway 管理端口 `8081` 的 `/actuator/**`：允许访问，用于 Prometheus 采集和健康检查。

这样既保护公网入口，又不影响本地可观测性。

## 6. 当前推荐表达

面试或项目说明中可以这样讲：

```text
Gateway 不再自己写 Redis 限流，避免和 Sentinel 重复治理。
Gateway 自定义过滤器只保留安全类横切能力，比如黑名单和内部路径保护。
流量治理统一使用 Sentinel Gateway Adapter，支持路由级、API 分组级、Header 参数级限流。
服务内部再用 SentinelResource / SphU 做业务参数限流，比如 userId、activityId、packageId。
```

## 7. 验证点

需要验证：

- Gateway 编译通过。
- `/internal/ping` 通过 Gateway 返回 `403`。
- `/actuator/health` 通过 Gateway 业务端口返回 `403` 或不可公开访问。
- `http://127.0.0.1:8081/actuator/health` 仍然可访问。
- Sentinel smoke 限流仍然返回 `429`。

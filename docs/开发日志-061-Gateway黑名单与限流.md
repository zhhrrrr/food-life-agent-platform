# 开发日志-061-Gateway黑名单与限流

## 本次目标

补齐 Gateway 作为统一入口必须具备的横切能力：

- 黑名单路径拦截
- IP 维度限流
- 用户维度限流
- Token 透传归一化
- 对应 local 配置、Nacos 配置、验证脚本

这一步仍然坚持原则：Gateway 只做横切能力，不承载业务逻辑。

## 新增与修改文件

### 1. Gateway 流量保护配置

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewaySecurityProperties.java
```

新增配置前缀：

```yaml
food:
  gateway:
    security:
```

包含两类配置：

- `blacklist`：黑名单路径
- `rate-limit`：限流配置

默认黑名单：

```text
/internal/**
/actuator/**
```

默认限流：

```text
IP 维度：60 秒 120 次
用户维度：60 秒 60 次
```

### 2. Gateway 流量保护过滤器

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayTrafficGuardFilter.java
```

职责：

- 放行浏览器预检 `OPTIONS`
- 命中黑名单直接返回 HTTP `403`
- 根据客户端 IP 做 Redis 计数窗口限流
- 根据 Token 做用户维度 Redis 计数窗口限流
- 命中限流返回 HTTP `429`
- 限流 Redis 异常时失败放行，避免 Redis 短暂抖动导致整个入口不可用

实现选择：

```text
这里使用 WebFlux WebFilter，而不是 Gateway GlobalFilter。
原因是 GlobalFilter 只会在 Gateway 匹配到路由后执行；黑名单通常需要覆盖未配置路由的敏感路径，例如 /internal/**、/actuator/**。
使用 WebFilter 后，所有进入 Gateway 的 HTTP 请求都会先经过黑名单和限流保护。
```

过滤器顺序：

```text
GatewayRequestLogFilter(WebFilter)
  -> GatewayTrafficGuardFilter(WebFilter)
  -> GatewayAuthFilter(GlobalFilter)
  -> Gateway 路由转发
  -> 下游微服务
```

这样被黑名单、限流、鉴权拒绝的请求也都会带上 `X-Trace-Id` 并输出请求日志。

### 3. Gateway Token 透传修正

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayAuthFilter.java
```

本次修正点：

- Gateway 仍然支持读取 `authorization`
- Gateway 也兼容 `Authorization: Bearer token`
- 通过 Redis 校验后，会把请求头归一化为原始 token 再转发给下游

原因：

黑马点评登录拦截器默认从请求头取原始 token，再拼接 Redis key：

```text
food:login:token:{token}
```

如果前端传 `Bearer token`，Gateway 自己可以识别，但下游如果继续收到 `Bearer token`，就可能无法识别用户。因此 Gateway 在通过鉴权后做一次请求头归一化。

### 4. local 配置

```text
food-gateway-service/src/main/resources/application-local.yml
```

新增：

```yaml
food:
  gateway:
    security:
      blacklist:
        enabled: true
        paths:
          - /internal/**
          - /actuator/**
      rate-limit:
        enabled: true
        ip:
          enabled: true
          capacity: 120
          window-seconds: 60
        user:
          enabled: true
          capacity: 60
          window-seconds: 60
```

local 模式用于本地兜底，不依赖 Nacos 配置中心。

### 5. Nacos 配置

```text
deploy/nacos/configs/food-gateway-service.yaml
```

同步新增同样的网关安全配置。

Nacos 模式启动时，Gateway 会通过：

```text
spring.config.import=optional:nacos:food-gateway-service.yaml
```

读取这一份配置。

### 6. 验证脚本

基础网关冒烟脚本：

```text
scripts/smoke-gateway.ps1
```

新增黑名单断言：

```text
GET /internal/ping -> HTTP 403, code=403
```

流量保护专项冒烟脚本：

```text
scripts/smoke-gateway-traffic-guards.ps1
```

验证内容：

- 黑名单路径返回 `403`
- 预置 IP 限流 Redis 计数后，请求返回 `429`
- 预置用户限流 Redis 计数后，请求返回 `429`

脚本不会暴力刷 120 次接口，而是直接用 `redis-cli setex` 预置当前时间窗口的计数，再发起一次请求验证效果。

## 当前 Gateway 十项能力完成状态

```text
1. 统一入口        已完成，8080 统一入口
2. 路由转发        已完成，通过 Gateway routes 转发 user/business/trade
3. CORS 跨域       已完成，通过 globalcors 配置
4. Token 透传      已完成，支持原始 token 和 Bearer token 归一化透传
5. 白名单路径      已完成，food.gateway.auth.exclude-paths
6. 黑名单路径      已完成，food.gateway.security.blacklist.paths
7. 请求日志        已完成，GatewayRequestLogFilter
8. traceId 生成    已完成，X-Trace-Id
9. IP 限流         已完成，Redis 计数窗口
10. 用户维度限流   已完成，Redis 计数窗口
```

## 面试表达

这一版可以这样讲：

```text
网关作为统一入口，只放横切能力，不写业务逻辑。
请求进入网关后先生成或透传 traceId，再经过黑名单和限流保护，然后做统一登录态粗校验，最后路由到具体微服务。
限流使用 Redis 固定窗口计数，支持 IP 维度和用户维度；黑名单、白名单、限流阈值都通过配置管理，local 和 Nacos 两套环境保持一致。
鉴权上兼容 Authorization: Bearer token，同时会把 token 归一化后透传给下游，保证和黑马点评登录拦截器兼容。
```

## 本地验证记录

Gateway 构建：

```powershell
mvn -pl food-gateway-service -am package -DskipTests
```

结果：通过。

Nacos 配置发布与读取：

```powershell
scripts/publish-nacos-configs.ps1
scripts/verify-nacos-configs.ps1
```

结果：`food-gateway-service.yaml` 已发布到 Nacos，并可正常读取。

Nacos 模式启动与注册发现：

```powershell
scripts/start-local-services-nacos.ps1 -Restart
scripts/verify-nacos-services.ps1
```

结果：

```text
food-gateway-service -> healthy
food-user-service -> healthy
food-business-service -> healthy
food-trade-service -> healthy
```

Gateway 基础冒烟：

```powershell
scripts/smoke-gateway.ps1
```

结果：

```text
/health -> 200, code=0000
/internal/ping -> 403, code=403
/api/user/me -> 401, code=401
/api/trade/orders -> 401, code=401
/api/shop-category/list -> 200, code=0000
/api/shop/1 -> 200, code=0000
/api/package/trade-snapshot/1 -> 200, code=0000
```

Gateway 黑名单与限流专项冒烟：

```powershell
scripts/smoke-gateway-traffic-guards.ps1
```

结果：

```text
黑名单 -> 403, code=403
IP 限流 -> 429, code=429
用户维度限流 -> 429, code=429
```

Bearer Token 透传验证：

```text
通过 Gateway 发送验证码 -> 登录 -> 使用 Authorization: Bearer {token} 调用 /api/user/me
```

结果：

```text
Bearer token passthrough ok, userId=1
```

全项目测试：

```powershell
mvn test
```

结果：通过。

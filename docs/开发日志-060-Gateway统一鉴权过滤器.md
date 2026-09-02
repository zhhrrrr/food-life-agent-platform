# 开发日志-060-Gateway统一鉴权过滤器

## 本次目标

在 Gateway 增加统一登录前置校验，让微服务入口具备生产项目常见的粗粒度鉴权能力。

当前项目仍然保留下游服务里的黑马点评登录拦截器，Gateway 鉴权是第一层入口拦截，下游服务鉴权是第二层兜底。

## 新增能力

新增 Gateway 全局鉴权过滤器：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayAuthFilter.java
```

它负责：

- 放行白名单接口
- 放行浏览器预检 `OPTIONS`
- 从请求头读取 `authorization`
- 兼容 `Bearer token` 写法
- 到 Redis 检查 `food:login:token:{token}` 是否存在
- token 不存在或 Redis 异常时返回 HTTP `401`
- 返回统一 JSON：`{"code":"401","message":"unauthorized"}`

## 配置抽象

新增 Gateway 鉴权配置类：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/properties/GatewayAuthProperties.java
```

配置前缀：

```yaml
food:
  gateway:
    auth:
```

支持配置：

- `enabled`
- `token-header`
- `token-prefix`
- `exclude-paths`

## 白名单规则

当前 Gateway 白名单：

```text
/health
/api/user/code
/api/user/login
/api/user/logout
/api/shop-category/**
/api/shop/**
/api/package/**
/api/trade/pay/callback/**
```

含义：

- 登录、验证码、健康检查放行
- 店铺分类、店铺详情、套餐详情作为公开浏览接口放行
- 支付回调入口放行，后续接真实支付时再做签名验签
- 订单、优惠券、收藏、评价发布等接口需要登录

## 配置位置

local 模式：

```text
food-gateway-service/src/main/resources/application-local.yml
```

Nacos 模式：

```text
deploy/nacos/configs/food-gateway-service.yaml
```

Redis 公共配置仍来自：

```text
deploy/nacos/configs/food-common.yaml
```

## 和下游认证的关系

Gateway 只做入口粗校验：

```text
有没有 token
Redis 里有没有这个 token
```

下游服务仍然负责：

```text
把 token 转成 LoginUserDTO
放入 UserHolder
做当前用户业务判断
```

也就是说，Gateway 不直接承担用户上下文构造职责，避免和黑马点评登录体系耦合过深。

## 改造后的请求链路

```text
前端
  ↓
GatewayRequestLogFilter 生成或透传 X-Trace-Id
  ↓
GatewayAuthFilter 检查登录态
  ↓
Gateway 路由到下游服务
  ↓
下游 RefreshTokenInterceptor 刷新 token 并设置 UserHolder
  ↓
下游 LoginInterceptor 兜底鉴权
  ↓
业务接口执行
```

## 面试表达

这一版可以这样讲：

```text
网关层做统一粗粒度鉴权，减少无效请求进入业务服务；
业务服务内部仍保留登录拦截器做纵深防御；
登录态沿用 Redis token，Gateway 只校验 token 是否存在，不侵入业务用户上下文；
白名单通过配置管理，支持 local 和 Nacos 两种模式。
```

## 本地验证记录

Gateway 构建：

- `mvn -pl food-gateway-service -am package -DskipTests`
- 结果：构建成功

Nacos 配置：

- `scripts/publish-nacos-configs.ps1`
- `scripts/verify-nacos-configs.ps1`
- 结果：`food-gateway-service.yaml` 已发布并可回读

Nacos 模式启动：

- `scripts/start-local-services-nacos.ps1 -Restart`
- 结果：Gateway、User、Business、Trade 四个服务健康

服务注册：

- `scripts/verify-nacos-services.ps1`
- 结果：四个服务全部注册到 Nacos，健康实例正常

Gateway 冒烟：

- `scripts/smoke-gateway.ps1`
- 结果：
  - `/health` 返回 `0000`
  - `/api/user/me` 未登录返回 HTTP `401`，业务码 `401`
  - `/api/trade/orders` 未登录返回 HTTP `401`，业务码 `401`
  - `/api/shop-category/list` 返回 `0000`
  - `/api/shop/1` 返回 `0000`
  - `/api/package/trade-snapshot/1` 返回 `0000`

完整业务穿透：

- 通过 Gateway 发送验证码
- 通过 Gateway 登录
- 通过 Gateway 获取当前用户
- 通过 Gateway 创建普通订单
- 结果：创建测试订单 `79`，订单状态 `WAIT_PAY`，支付金额 `16800`

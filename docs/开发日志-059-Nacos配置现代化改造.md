# 开发日志-059-Nacos配置现代化改造

## 本次目标

把 Nacos 客户端配置从旧的 `bootstrap.yml` 模式迁移到 Spring Boot 2.4+ 更主流的 Config Data 模式：

- 使用 `application.yml`
- 使用 `spring.config.import`
- 移除 `spring-cloud-starter-bootstrap`
- 区分 `local` 和 `nacos` 两种启动 profile
- 让 Nacos 模式真正通过注册中心发现服务

## 为什么要改

旧方式：

```text
bootstrap.yml + spring-cloud-starter-bootstrap
```

这是 Spring Cloud 老项目常见写法。

新方式：

```text
application.yml + spring.config.import
```

这是 Spring Boot 2.4 之后的 Config Data API 写法，更贴近现在的主流 Spring Cloud 项目和面试表达。

## 改造后的配置结构

每个服务现在都拆成两类配置：

```text
application.yml
application-local.yml
```

其中：

- `application.yml`：只放服务名、默认 profile、Nacos Config Data 导入逻辑
- `application-local.yml`：只放本地直启兜底配置
- `deploy/nacos/configs/*.yaml`：作为 Nacos 模式的主配置来源

## application.yml 负责什么

四个服务的 `application.yml` 都采用相同结构：

```yaml
spring:
  application:
    name: food-user-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

---
spring:
  config:
    activate:
      on-profile: nacos
    import:
      - optional:nacos:food-common.yaml?group=${NACOS_CONFIG_GROUP:FOOD_LIFE_AGENT}&refreshEnabled=true
      - optional:nacos:food-user-service.yaml?group=${NACOS_CONFIG_GROUP:FOOD_LIFE_AGENT}&refreshEnabled=true
```

核心含义：

- 默认走 `local`
- 只有启用 `nacos` profile 时，才导入 Nacos 配置
- Nacos 配置分为公共配置和服务私有配置

## application-local.yml 负责什么

`application-local.yml` 负责不用 Nacos 时也能本地启动。

里面保留：

- `server.port`
- `datasource`
- `redis`
- `mybatis-plus`
- `food.auth`
- 本地 `simple.instances`
- Gateway 本地路由

本地固定服务地址只保留在这里，不再放进 Nacos 配置中心。

## Nacos 配置中心负责什么

Nacos 配置文件仍然在：

```text
deploy/nacos/configs
```

包含：

- `food-common.yaml`
- `food-user-service.yaml`
- `food-business-service.yaml`
- `food-trade-service.yaml`
- `food-gateway-service.yaml`

本次移除了 Nacos 配置里的 `simple.instances`。

Nacos 模式下：

- Gateway 通过 `lb://food-user-service` 路由
- Gateway 通过 `lb://food-business-service` 路由
- Gateway 通过 `lb://food-trade-service` 路由
- Feign 通过 `@FeignClient(name = "...")` 调服务

也就是说，Nacos 模式不再依赖本地写死实例。

## 启动脚本变化

普通本地启动：

```powershell
.\scripts\start-local-services.ps1
```

默认设置：

```text
SPRING_PROFILES_ACTIVE=local
NACOS_DISCOVERY_ENABLED=false
NACOS_CONFIG_ENABLED=false
```

Nacos 模式启动：

```powershell
.\scripts\start-local-services-nacos.ps1
```

默认设置：

```text
SPRING_PROFILES_ACTIVE=nacos
NACOS_DISCOVERY_ENABLED=true
NACOS_CONFIG_ENABLED=true
```

## 改造后的理解方式

现在配置链路变成：

```text
启动脚本
  ↓
设置 SPRING_PROFILES_ACTIVE
  ↓
application.yml
  ↓
local profile：读取 application-local.yml
nacos profile：通过 spring.config.import 读取 Nacos
  ↓
服务启动
```

这比之前的：

```text
bootstrap.yml + application.yml + Nacos yaml
```

更清楚，也更符合现在的 Spring Boot / Spring Cloud 写法。

## 本地验证记录

后端构建：

- `mvn package -DskipTests`
- 结果：25 个 Maven 模块全部 `SUCCESS`

local 模式：

- `scripts/start-local-services.ps1 -IncludeGateway -Restart`
- `scripts/smoke-gateway.ps1`
- 结果：四个服务启动成功，网关基础冒烟通过

Nacos 配置：

- `scripts/publish-nacos-configs.ps1`
- `scripts/verify-nacos-configs.ps1`
- 结果：5 个 Nacos 配置全部发布并可回读

Nacos 模式：

- `scripts/start-local-services-nacos.ps1 -Restart`
- `scripts/verify-nacos-services.ps1`
- `scripts/smoke-gateway.ps1`
- 结果：四个服务全部注册到 Nacos，Gateway 通过 `lb://` 路由成功

完整业务穿透：

- 通过 Gateway 发送验证码
- 通过 Gateway 登录
- 通过 Gateway 获取当前用户
- 通过 Gateway 创建普通订单
- 结果：创建测试订单 `78`，订单状态 `WAIT_PAY`，支付金额 `16800`

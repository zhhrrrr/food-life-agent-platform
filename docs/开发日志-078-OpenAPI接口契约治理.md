# 开发日志-078-OpenAPI接口契约治理

## 本次目标

补齐接口契约治理，让 user、business、trade 三个业务服务可以自动生成 OpenAPI 文档，并让 Gateway 聚合三个服务的接口文档入口。

这一步是 Agent 前非常重要的基础能力。后续无论是前端联调、服务间接口 review，还是把业务接口包装成 Agent 工具，都需要稳定的接口契约。

## 技术选型

本项目使用 Spring Boot 3，因此接入 springdoc-openapi starter：

- WebMVC 业务服务使用 `springdoc-openapi-starter-webmvc-ui`
- Gateway WebFlux 服务使用 `springdoc-openapi-starter-webflux-ui`

当前项目 Boot 版本为 `3.0.13`，因此本次选择 `springdoc-openapi` 的 `2.2.0` 版本，避免直接追最新版导致 Spring Boot 3.0 早期线兼容风险。

## Maven 改造

### 1. 根 pom 增加版本和依赖管理

文件：

- `pom.xml`

新增：

- `springdoc-openapi.version`
- `springdoc-openapi-starter-webmvc-ui`
- `springdoc-openapi-starter-webflux-ui`

### 2. user-service 接入 OpenAPI

文件：

- `food-user-service/food-user-app/pom.xml`

新增依赖：

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

### 3. business-service 接入 OpenAPI

文件：

- `food-business-service/food-business-app/pom.xml`

新增依赖：

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

### 4. trade-service 接入 OpenAPI

文件：

- `food-trade-service/food-trade-app/pom.xml`

新增依赖：

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

### 5. gateway 接入 Swagger UI 聚合入口

文件：

- `food-gateway-service/pom.xml`

新增依赖：

- `org.springdoc:springdoc-openapi-starter-webflux-ui`

## OpenAPI 元信息

### 1. user-service

文件：

- `food-user-service/food-user-app/src/main/java/com/foodlife/user/config/OpenApiConfiguration.java`

定义：

- 文档标题：`Food Life User Service API`
- 范围：登录、用户资料、用户社交关系
- 安全声明：`authorization` Bearer Token

### 2. business-service

文件：

- `food-business-service/food-business-app/src/main/java/com/foodlife/business/config/OpenApiConfiguration.java`

定义：

- 文档标题：`Food Life Business Service API`
- 范围：店铺、套餐、收藏、评价
- 安全声明：`authorization` Bearer Token

### 3. trade-service

文件：

- `food-trade-service/food-trade-app/src/main/java/com/foodlife/trade/config/OpenApiConfiguration.java`

定义：

- 文档标题：`Food Life Trade Service API`
- 范围：普通购买、拼团、秒杀、支付、退款、核销
- 安全声明：`authorization` Bearer Token

## 本地配置

### 1. user-service

文件：

- `food-user-service/food-user-app/src/main/resources/application-local.yml`

新增：

- `/v3/api-docs`
- `/swagger-ui.html`
- auth 放行 Swagger 相关路径

### 2. business-service

文件：

- `food-business-service/food-business-app/src/main/resources/application-local.yml`

新增：

- `/v3/api-docs`
- `/swagger-ui.html`
- auth 放行 Swagger 相关路径

### 3. trade-service

文件：

- `food-trade-service/food-trade-app/src/main/resources/application-local.yml`

新增：

- `/v3/api-docs`
- `/swagger-ui.html`
- auth 放行 Swagger 相关路径

### 4. gateway

文件：

- `food-gateway-service/src/main/resources/application-local.yml`

新增 Swagger UI 聚合：

- `user-service` -> `/v3/api-docs/user`
- `business-service` -> `/v3/api-docs/business`
- `trade-service` -> `/v3/api-docs/trade`

新增 Gateway 路由：

- `/v3/api-docs/user` -> `food-user-service:/v3/api-docs`
- `/v3/api-docs/business` -> `food-business-service:/v3/api-docs`
- `/v3/api-docs/trade` -> `food-trade-service:/v3/api-docs`

Gateway auth 放行：

- `/v3/api-docs/**`
- `/swagger-ui.html`
- `/swagger-ui/**`

## Nacos 配置同步

同步修改：

- `deploy/nacos/configs/food-user-service.yaml`
- `deploy/nacos/configs/food-business-service.yaml`
- `deploy/nacos/configs/food-trade-service.yaml`
- `deploy/nacos/configs/food-gateway-service.yaml`

原因：

- 项目现在支持 `local` 和 `nacos` 两种 profile。
- 如果只改本地 YAML，不改 Nacos 配置，切换到 Nacos 后 OpenAPI 会失效。

## 访问方式

### 1. 通过 Gateway 统一查看

推荐入口：

```text
http://localhost:8080/swagger-ui.html
```

Swagger UI 下拉可以选择：

- `user-service`
- `business-service`
- `trade-service`

### 2. 直接访问单服务

user-service：

```text
http://localhost:8101/swagger-ui.html
http://localhost:8101/v3/api-docs
```

business-service：

```text
http://localhost:8201/swagger-ui.html
http://localhost:8201/v3/api-docs
```

trade-service：

```text
http://localhost:8301/swagger-ui.html
http://localhost:8301/v3/api-docs
```

## 对后续 Agent 的价值

Agent 接口工具化时，可以从 OpenAPI 契约里获得：

- 请求路径
- HTTP method
- 入参 schema
- 响应 schema
- 鉴权方式
- 错误返回结构

后续如果要做 Java 侧工具注册，可以把 OpenAPI 作为工具元数据来源之一，而不是人工逐个写接口说明。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
```

结果：

- 全模块编译通过。

## 当前边界

本次完成的是 OpenAPI 基础接入和 Gateway 聚合。

后续还可以继续增强：

- 给核心 DTO 增加 `@Schema` 字段说明。
- 给核心 Controller 增加 `@Tag`、`@Operation`。
- 用分组区分公网 API、内部 API、运营 API。
- 为 Agent 工具调用生成更严格的接口白名单。

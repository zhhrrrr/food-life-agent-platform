# 开发日志-069-Boot3可观测性与 Micrometer Tracing

## 一、本次目标

本次开始补齐微服务生产化里的可观测性能力，参考 xfg 项目里 Prometheus/Grafana 的思路，但按当前项目技术栈升级为 Spring Boot 3 + Micrometer Tracing。

本次覆盖：

```text
1. Actuator 健康检查
2. Prometheus 指标暴露
3. Grafana 看板
4. traceId 全链路日志
5. 统一日志格式
6. 慢接口统计
7. Feign 调用耗时日志
8. Boot 3 / Java 17 基线迁移
```

## 二、为什么先升级 Boot 3

Spring Boot 2.x 时代主要是 Spring Cloud Sleuth。

Spring Boot 3 以后主流链路追踪迁移到：

```text
Micrometer Observation
Micrometer Tracing
```

所以这一步不是单纯加 Actuator，而是顺手把技术基线升级到更贴近现在公司项目和面试讨论的版本。

## 三、核心依赖改造

根工程：

```text
pom.xml
```

调整内容：

```text
Spring Boot 2.7.12 -> 3.0.13
Java 1.8 -> 17
Spring Cloud 2021.0.8 -> 2022.0.5
Spring Cloud Alibaba 2021.0.5.0 -> 2022.0.0.0
MyBatis Plus starter -> mybatis-plus-spring-boot3-starter
MySQL driver -> com.mysql:mysql-connector-j
```

新增公共模块：

```text
food-observability-starter
```

它被接入到了：

```text
food-user-service/food-user-app
food-business-service/food-business-app
food-trade-service/food-trade-app
food-gateway-service
```

## 四、可观测性 starter 做了什么

新增模块：

```text
food-observability-starter
```

核心文件：

```text
food-observability-starter/src/main/java/com/foodlife/observability/config/ObservabilityAutoConfiguration.java
food-observability-starter/src/main/java/com/foodlife/observability/filter/ServletTraceFilter.java
food-observability-starter/src/main/java/com/foodlife/observability/filter/ReactiveTraceWebFilter.java
food-observability-starter/src/main/java/com/foodlife/observability/feign/ObservabilityFeignLogger.java
food-observability-starter/src/main/java/com/foodlife/observability/properties/ObservabilityProperties.java
food-observability-starter/src/main/java/com/foodlife/observability/support/TraceIdSupport.java
```

能力说明：

```text
ServletTraceFilter
用于 user/business/trade 这类 Spring MVC 服务。
进入接口时生成或继承 X-Trace-Id，写入 MDC，响应头回传 traceId，记录接口耗时和慢接口指标。

ReactiveTraceWebFilter
用于 gateway 这类 WebFlux 服务。
逻辑与 ServletTraceFilter 对齐，保证网关入口也能生成 traceId。

ObservabilityFeignLogger
用于 OpenFeign。
记录远程调用 client、method、url、status、durationMs，超过阈值输出 warn。

ObservabilityProperties
统一配置开关、trace header、慢接口阈值、慢 Feign 阈值。
```

## 五、Actuator 和 Prometheus

统一暴露：

```text
/actuator/health
/actuator/prometheus
/actuator/metrics
```

各服务本地管理端口：

```text
gateway   8081
user      8102
business  8202
trade     8302
```

配置位置：

```text
deploy/nacos/configs/food-common.yaml
deploy/nacos/configs/food-gateway-service.yaml
deploy/nacos/configs/food-user-service.yaml
deploy/nacos/configs/food-business-service.yaml
deploy/nacos/configs/food-trade-service.yaml
```

本地兜底配置：

```text
food-gateway-service/src/main/resources/application-local.yml
food-user-service/food-user-app/src/main/resources/application-local.yml
food-business-service/food-business-app/src/main/resources/application-local.yml
food-trade-service/food-trade-app/src/main/resources/application-local.yml
```

本地默认关闭 RabbitMQ health：

```yaml
management:
  health:
    rabbit:
      enabled: ${FOOD_RABBIT_HEALTH_ENABLED:false}
```

原因：

```text
当前本地可以通过 FOOD_MQ_ENABLED=false 关闭 RabbitMQ 业务消费者。
如果不关闭 Rabbit health，Actuator 会因为 RabbitMQ 未启动返回 503，影响 Prometheus 抓取服务可用性。
真实启用 RabbitMQ 时可以设置 FOOD_RABBIT_HEALTH_ENABLED=true。
```

## 六、Grafana 看板

新增部署目录：

```text
deploy/observability
```

包含：

```text
deploy/observability/docker-compose.yml
deploy/observability/prometheus/prometheus.yml
deploy/observability/grafana/provisioning/datasources/prometheus.yml
deploy/observability/grafana/provisioning/dashboards/food-life-agent.yml
deploy/observability/grafana/dashboards/food-life-agent-overview.json
```

默认访问：

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:4000
账号密码:    admin / admin
```

Grafana 当前看板包含：

```text
Service Up
HTTP QPS
HTTP P95 Latency
Slow API Count
JVM Memory Used
Process CPU Usage
```

## 七、traceId 全链路日志

统一 trace header：

```text
X-Trace-Id
```

每个服务新增：

```text
logback-spring.xml
```

日志格式统一包含：

```text
application
traceId
spanId
thread
logger
message
```

示例：

```text
2026-09-06 10:00:00.000 INFO [food-trade-service,traceId=abc,spanId=def] [http-nio-8301-exec-1] c.f.xxx - message
```

## 八、慢接口统计

慢接口阈值：

```yaml
food:
  observability:
    slow-api-threshold-ms: 1000
```

超过阈值时：

```text
1. 日志输出 warn
2. Prometheus 记录 food_http_server_slow_request_total
3. Prometheus 记录 food_http_server_request_duration
```

后续可以在 Grafana 上看慢接口数量和接口耗时分位线。

## 九、Feign 调用耗时

OpenFeign 配置：

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
      micrometer:
        enabled: true
      client:
        config:
          default:
            loggerLevel: basic

feign:
  sentinel:
    enabled: false
```

自定义日志：

```text
ObservabilityFeignLogger
```

日志会记录：

```text
client
method
url
status
durationMs
```

超过：

```yaml
food:
  observability:
    slow-feign-threshold-ms: 800
```

输出 warn。

说明：

```text
Boot 3 下不再使用旧的 feign.sentinel.enabled Sentinel Feign Builder。
当前改为 Spring Cloud OpenFeign CircuitBreaker 接 Sentinel，避免 Feign 退回默认 Contract 导致 @GetMapping 不生效。
```

## 十、Actuator 放行修补

为了避免监控端点被业务鉴权挡住，本次补了：

```text
food-auth-starter/src/main/java/com/foodlife/auth/config/AuthAutoConfiguration.java
```

默认放行：

```text
/actuator/**
```

为了避免 Gateway 黑名单误伤独立管理端口，本次补了：

```text
food-gateway-service/src/main/java/com/foodlife/gateway/filter/GatewayTrafficGuardFilter.java
```

当请求路径为 `/actuator/**` 时直接放行给 Actuator。

## 十一、本地 Java 版本脚本

新增：

```text
scripts/use-java17-plus.ps1
```

作用：

```text
自动寻找本机 Java 17+ JDK
设置 JAVA_HOME
把 JAVA_HOME/bin 放到当前 PowerShell PATH 最前面
```

`scripts/start-local-services.ps1` 已改为启动前调用这个脚本，确保 Boot 3 服务不会被 Java 8 启动。

同时 `scripts/start-local-services-nacos.ps1` 的 Nacos 用户名和密码默认改为空。

原因：

```text
本地 Nacos 压缩包是未开启鉴权模式。
默认传 nacos/nacos 会触发客户端反复登录，日志出现 Nacos auth login failed。
如果后续开启 Nacos 鉴权，再通过 -NacosUsername 和 -NacosPassword 显式传入。
```

## 十二、验证脚本

新增：

```text
scripts/smoke-observability.ps1
```

验证范围：

```text
GET http://localhost:8081/actuator/health
GET http://localhost:8102/actuator/health
GET http://localhost:8202/actuator/health
GET http://localhost:8302/actuator/health

GET /actuator/prometheus
校验 jvm_memory_used_bytes 指标存在
```

## 十三、本次验证

已完成：

```text
mvn clean install -DskipTests
mvn test
scripts/smoke-observability.ps1
scripts/smoke-gateway.ps1
scripts/verify-nacos-services.ps1
scripts/smoke-operation-stock-adjustment.ps1
```

结果：

```text
BUILD SUCCESS
Observability smoke verification completed
Gateway smoke verification completed
Nacos service verification completed
Operation stock adjustment smoke verification completed
```

说明：

```text
Boot 3 迁移、Jakarta 包迁移、MyBatis Plus Boot3 starter、Gateway WebFlux HttpStatusCode 类型调整均已通过编译。
四个服务均已通过 /actuator/health 和 /actuator/prometheus 验证。
运营库存调整链路触发 trade -> business Feign 调用，日志中已出现 feign request finished 和 durationMs。
本机当前未安装 docker 命令，所以 Prometheus/Grafana 容器未实际启动；部署文件已经落地。
```

# 开发日志-054-Nacos注册配置中心基线

## 1. 本次目标

本次开始微服务治理改造的第一步：给现有三个业务服务接入 Nacos 注册中心和配置中心能力。

当前仍然保持服务数量少的拆分方式：

```text
food-user-service
food-business-service
food-trade-service
```

本次不改变业务逻辑，不改 Controller、Domain、Repository，只补齐微服务基础设施。

## 2. 为什么先做 Nacos

当前项目的服务间调用仍然依赖固定地址：

```text
food-business-service -> http://localhost:8301
food-trade-service    -> http://localhost:8201
```

这在本地可以运行，但还不是标准微服务形态。接入 Nacos 后，后续可以继续推进：

```text
1. 服务注册与发现
2. OpenFeign 服务名调用
3. Gateway 动态路由
4. Sentinel 按服务治理限流熔断
5. 配置中心统一管理环境配置
```

## 3. 新增依赖

父工程新增 Spring Cloud Alibaba 版本管理：

```xml
<spring-cloud-alibaba.version>2021.0.5.0</spring-cloud-alibaba.version>
```

三个 app 模块新增：

```text
spring-cloud-starter-bootstrap
spring-cloud-starter-alibaba-nacos-discovery
spring-cloud-starter-alibaba-nacos-config
```

## 4. 新增 bootstrap 配置

三个服务分别新增：

```text
food-user-service/food-user-app/src/main/resources/bootstrap.yml
food-business-service/food-business-app/src/main/resources/bootstrap.yml
food-trade-service/food-trade-app/src/main/resources/bootstrap.yml
```

配置内容统一包含：

```text
spring.application.name
spring.cloud.nacos.server-addr
spring.cloud.nacos.discovery
spring.cloud.nacos.config
shared-configs: food-common.yaml
```

## 5. 双模式启动

为了不破坏当前本地开发基线，本次采用双模式：

```text
默认模式：
Nacos discovery/config 默认关闭，仍然可以按原方式本地启动。

Nacos 模式：
通过脚本显式打开 NACOS_DISCOVERY_ENABLED 和 NACOS_CONFIG_ENABLED。
```

新增脚本：

```powershell
.\scripts\start-local-services-nacos.ps1
```

示例：

```powershell
.\scripts\start-local-services-nacos.ps1 -Rebuild -Restart
```

指定 Nacos 地址：

```powershell
.\scripts\start-local-services-nacos.ps1 -NacosServerAddr "127.0.0.1:8848"
```

## 6. 当前边界

本次只是 Nacos 基线，不包含：

```text
1. RestTemplate 改 OpenFeign
2. Gateway 路由
3. Sentinel 限流熔断
4. RocketMQ 消息化
5. Seata 分布式事务
```

这些会放到后续步骤继续推进。

## 7. 面试点对应

本次可以对应以下面试点：

```text
1. 什么是服务注册与发现
2. 为什么微服务不能写死 IP 和端口
3. Nacos 服务注册流程
4. Nacos 配置中心作用
5. bootstrap.yml 和 application.yml 的区别
6. 为什么改造时要保留本地直连模式
```

## 8. 下一步

下一步建议把当前 RestTemplate 跨服务调用改造成 OpenFeign：

```text
food-trade-service -> food-business-service
food-business-service -> food-trade-service
```

这样才能真正使用服务名调用和注册中心发现能力。

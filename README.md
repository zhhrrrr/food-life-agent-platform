# food-life-agent-platform

美食生活业务平台，当前阶段先完成 Agent 之前的业务底座：用户、店铺套餐、交易下单、拼团、秒杀、支付回调骨架、退款、核销、评价、收藏、关注、网关、限流熔断、消息最终一致性、分布式事务示例链路与可观测性。

项目采用微服务 + DDD 分层，保留后续接入业务 Agent 与 Python Runtime 的边界。

## 架构

```text
food-life-agent-platform
├── food-gateway-service          # 统一入口、路由、CORS、鉴权、黑名单、限流、traceId
├── food-user-service             # 登录、用户资料、关注关系
├── food-business-service         # 店铺、分类、套餐、库存、评价、收藏、店铺主页
├── food-trade-service            # 普通购买、拼团、秒杀、支付、退款、核销、订单查询
├── food-auth-starter             # Redis Token、UserHolder、Feign Token 透传、内部调用保护
├── food-observability-starter    # traceId、统一日志、慢接口、Feign 耗时、Actuator、Prometheus
├── food-domain-patterns          # xfg 风格责任链、规则树等领域设计模式模板
└── food-life-agent-web           # Vue 3 用户端前端
```

## 技术栈

| 方向 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3、Spring Cloud、Spring Cloud Alibaba |
| 架构 | 微服务、DDD、OpenFeign、Gateway、Nacos |
| 数据 | MySQL、Redis、MyBatis-Plus |
| 高并发治理 | Sentinel、Redis 预扣、幂等、补偿 |
| 消息 | RabbitMQ、本地消息表、最终一致性 |
| 分布式事务 | Seata AT，仅用于适合强一致展示的退款确认链路 |
| 可观测性 | Actuator、Micrometer、Prometheus、Grafana、traceId 日志 |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Axios、Element Plus |

## 本地基础设施

| 组件 | 地址 |
| --- | --- |
| MySQL | `127.0.0.1:3306` |
| Redis | `127.0.0.1:6379` |
| Nacos | `http://127.0.0.1:8848/nacos` |
| RabbitMQ | `127.0.0.1:5672` |
| RabbitMQ Management | `http://127.0.0.1:15672` |
| Seata Server | `127.0.0.1:8091` |
| Sentinel Dashboard | `http://127.0.0.1:8858` |

默认账号见 [数据记录-本地微服务运行组件.md](docs/数据记录-本地微服务运行组件.md)。

## 快速启动

首次准备并启动基础设施：

```powershell
.\scripts\start-infra-all.ps1
```

启动后端服务和网关：

```powershell
.\scripts\start-local-services.ps1 -IncludeGateway -Restart
```

运行本地业务验收：

```powershell
.\scripts\smoke-test-local-microservices.ps1
```

启动前端：

```powershell
cd food-life-agent-web
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 常用验证

```powershell
.\scripts\verify-company-readiness.ps1
```

该脚本会统一执行：

- Java 17+ 环境切换
- 微服务边界检查
- Nacos 配置检查
- Maven 测试
- 前端构建
- 基础设施和本地 smoke test，前提是本地服务已经启动

## 服务端口

| 服务 | 业务端口 | Actuator 端口 |
| --- | --- | --- |
| Gateway | `8080` | `8081` |
| user-service | `8101` | `8102` |
| business-service | `8201` | `8202` |
| trade-service | `8301` | `8302` |

接口统一从 Gateway 进入：

```text
/api/user/**
/api/shop-category/**
/api/shop/**
/api/package/**
/api/shop-homepage/**
/api/reviews/**
/api/favorites/shops/**
/api/trade/**
```

## 数据库

本地默认：

```text
MySQL localhost:3306 root/root
Redis localhost:6379
```

SQL：

```text
docs/sql/food_user_db.sql
docs/sql/food_business_db.sql
docs/sql/food_trade_db.sql
```

## 当前业务能力

- 黑马点评风格手机验证码登录
- Redis Token 与登录态刷新
- Gateway 鉴权、Token 透传、内部接口保护
- 店铺分类、店铺列表、套餐详情、套餐交易快照
- 普通购买订单：下单、支付、取消、超时取消、退款、核销
- 拼团订单：锁单、成团判断、超时补偿、退款回滚、查询
- 秒杀订单：活动查询、Redis 库存预扣、异步请求单、恢复、对账
- 优惠券：领取、适用范围、限领、过期扫描、退回
- 评价：创建、列表、摘要、异步更新统计、幂等消费
- 收藏、关注、用户资料、个人主页、店铺主页聚合
- RabbitMQ 事件发布、延迟关单、本地消息表补偿
- Sentinel Gateway/API/热点参数/用户维度限流
- Seata AT 退款确认链路
- Actuator、Prometheus、Grafana 看板配置、traceId 全链路日志

## Agent 前置清单

Agent 之前还需要完成的事项见：

- [Agent前置完成清单.md](docs/Agent前置完成清单.md)

# Agent 前置完成清单

## 已经具备的业务底座

- 微服务拆分已经稳定为少量核心服务：Gateway、user、business、trade。
- DDD 分层已经形成：api、types、domain、infrastructure、trigger、app。
- Gateway 已承担统一入口、路由、CORS、鉴权、Token 透传、黑名单、限流、traceId。
- 登录沿用黑马点评 Redis Token 方案，并抽成 `food-auth-starter`。
- 服务间调用使用 OpenFeign + Nacos LoadBalancer + 内部调用头。
- 普通购买、拼团、秒杀三个交易主流程已实现。
- RabbitMQ 已用于订单事件、库存事件、评价事件、延迟关单。
- Sentinel 已覆盖 Gateway 路由/API 限流、trade 下单限流、business 库存热点参数限流。
- Seata AT 已落到退款确认这种适合展示强一致的链路。
- Actuator、Prometheus、Grafana、traceId 日志、慢接口、Feign 耗时日志已经接入。
- 前端已经走 Gateway 联调，不再绕过微服务入口。
- 本地一键启动、smoke test、CI 基础验证已具备。

## Agent 之前建议继续补齐

### P0 必做

1. 接口契约治理（基础版已完成）
   - 已接入 OpenAPI，user、business、trade 可各自暴露 `/v3/api-docs`。
   - Gateway 已聚合 user、business、trade 三个服务的 OpenAPI 文档入口。
   - 后续增强：继续为核心 DTO 增加字段说明、错误码说明、典型请求响应，并按公网/内部/运营/Agent 工具分组。

2. 统一错误码和异常治理（基础版已完成）
   - 已为 user、business、trade 增加统一 `ErrorCode`。
   - 已为 user、business、trade 增加 `GlobalExceptionHandler`。
   - Gateway 鉴权、黑名单、Sentinel 限流错误响应已收敛。
   - 后续增强：继续把低频 Controller 中散落的 `try/catch` 迁移到统一异常处理。

3. 真实支付边界
   - 当前支付、退款、核销仍是 mock 或本地骨架。
   - 在 Agent 接入前至少要抽象支付渠道接口、支付回调验签、退款单、核销码。
   - 可以保留 mock 实现，但要明确它是 `local` provider。

4. 权限模型（基础版已完成）
   - 登录仍沿用黑马点评 Redis Token。
   - Token 用户上下文已增加 `role`。
   - `food-auth-starter` 已增加角色路径拦截器。
   - 运营库存调整、退款确认已配置为 `ADMIN/OPERATOR` 才能访问。
   - 后续增强：补运营后台账号、商家员工表、商家端核销正式接口、写操作审计。

5. Agent 工具权限表设计
   - 哪些接口允许 Agent 调用。
   - 哪些工具只能读，哪些工具可写。
   - 写操作需要二次确认、幂等 key、操作审计。

### P1 建议做

1. 接口级自动化测试
   - 把现有 smoke script 拆成登录、交易、库存、MQ、网关安全几组。
   - 增加测试数据准备与清理脚本。

2. 审计日志
   - 库存调整、退款确认、核销、Agent 写操作都应有审计。
   - 审计字段至少包含 traceId、operatorId、operatorType、source、bizType、bizId、before/after。

3. 配置分环境治理
   - 当前 local 已可跑。
   - 后续建议补 `dev`、`test`、`prod` Nacos namespace/group 规范。
   - 密码、internal secret、数据库账号不要硬编码在生产配置。

4. 数据库迁移工具
   - 当前是 SQL 文件手动执行。
   - 公司项目更常见 Flyway/Liquibase 管理版本。
   - Agent 前不是绝对阻塞，但越早接入越稳。

5. 可观测性继续增强
   - Grafana 看板导入脚本。
   - RabbitMQ 队列积压告警。
   - Sentinel 规则变更记录。
   - Feign 熔断次数、降级次数指标。

### P2 后续增强

1. 灰度和压测
   - 秒杀、拼团、下单适合补 JMeter/k6 压测脚本。
   - 面试可以讲 QPS、瓶颈、优化前后对比。

2. 缓存一致性
   - 店铺、套餐、评价摘要可以逐步补缓存更新策略。
   - 明确旁路缓存、延迟双删、MQ 更新缓存的适用场景。

3. CI/CD
   - 当前 CI 能跑测试和前端构建。
   - 后续可补 Docker 镜像构建、配置校验、制品归档。

4. Agent Runtime
   - 等业务底座稳定后再建 `food-agent-service` 和 `python-agent-runtime`。
   - Java 侧负责业务权限、工具注册、审计、会话任务。
   - Python 侧负责模型编排、RAG、工具规划、复杂推理。

## 我的判断

现在最接近 Agent 的阻塞点不是微服务组件，而是“契约、权限、错误语义、审计”。Agent 会把后端接口当工具调用，如果这些边界不稳定，Agent 会放大问题。

所以下一步更推荐先做：

1. 统一错误码与全局异常处理。
2. OpenAPI/接口契约。
3. 运营/商家权限模型。
4. 写操作审计日志。

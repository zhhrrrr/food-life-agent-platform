# 开发日志-080-trade写操作审计日志

## 本次目标

补齐 trade-service 的关键写操作审计日志，让支付、退款、取消、核销、运营库存调整这些动作可追踪、可回溯。

这一步不是为了展示日志，而是为了让项目更接近公司业务系统：高风险写操作必须知道“谁在什么链路下，对哪个业务对象做了什么”。

## 本次新增表

文件：

- `docs/sql/food_trade_db.sql`
- `docs/sql/food_trade_db_migration_080_operation_audit_log.sql`

新增表：

- `trade_operation_audit_log`

核心字段：

- `trace_id`：全链路 traceId
- `operator_id`：操作人用户 ID
- `operator_role`：操作人角色
- `operation_type`：操作类型
- `biz_type`：业务对象类型
- `biz_id`：业务对象 ID
- `operation_status`：操作结果
- `request_content`：请求快照
- `response_content`：响应快照
- `remark`：备注
- `create_time`：审计时间

## DDD 分层落点

### domain 层

新增：

- `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/audit/model/OperationAuditCommandEntity.java`
- `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/audit/model/OperationAuditLogEntity.java`
- `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/audit/repository/IOperationAuditRepository.java`
- `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/audit/service/OperationAuditDomainService.java`

职责：

- 定义审计命令
- 定义审计日志实体
- 定义仓储接口
- 校验审计必要字段

### infrastructure 层

新增：

- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/dao/po/OperationAuditLogPO.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/dao/IOperationAuditLogMapper.java`
- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OperationAuditRepository.java`

职责：

- MyBatis-Plus 表映射
- 写入 `trade_operation_audit_log`
- PO 转领域实体

### trigger/app 层

新增：

- `food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/app/OperationAuditApplicationService.java`

职责：

- 从 `UserHolder` 获取操作人
- 从 MDC 获取 `traceId`
- 使用 `ObjectMapper` 保存请求/响应快照
- 捕获审计失败异常，避免影响主业务

## 已接入审计的业务

| operation_type | 触发接口 | biz_type | 说明 |
| --- | --- | --- | --- |
| PAYMENT_PREPARE | POST /api/trade/pay/orders/{orderId}/prepare | ORDER | 创建或复用支付单 |
| PAYMENT_CALLBACK_MOCK | POST /api/trade/pay/callback/mock | PAYMENT_ORDER | 本地 mock 支付回调 |
| ORDER_CANCEL | POST /api/trade/orders/{orderId}/cancel | ORDER | 用户取消订单 |
| ORDER_PAY_MOCK | POST /api/trade/orders/{orderId}/pay/mock | ORDER | 旧版本地 mock 支付 |
| USER_REFUND_APPLY | POST /api/trade/orders/{orderId}/refund/apply | ORDER | 用户申请退款 |
| OPERATION_REFUND_CONFIRM | POST /api/trade/orders/{orderId}/refund/confirm | ORDER | 运营确认退款 |
| ORDER_USE_MOCK | POST /api/trade/orders/{orderId}/use/mock | ORDER | 到店核销 |
| OPERATION_STOCK_ADJUST | POST /api/trade/operations/package-stock-adjustments | PACKAGE_STOCK | 运营调整套餐库存 |

## 请求链路示例

以“支付成功回调”为例：

1. 前端或本地脚本调用支付回调。
2. Gateway 生成或透传 traceId。
3. Gateway 路由到 trade-service。
4. `PaymentController.mockPaySuccessCallback` 接收请求。
5. `PaymentOrderService.handlePaySuccessCallback` 更新支付单。
6. `OrderPaySettlementService` 执行支付结算。
7. 订单状态变为 `PAID`。
8. business-service 确认套餐销量。
9. RabbitMQ 发布支付成功事件。
10. `OperationAuditApplicationService` 读取 `UserHolder` 和 MDC。
11. 组装 `OperationAuditCommandEntity`。
12. `OperationAuditDomainService` 校验审计命令。
13. `OperationAuditRepository` 写入 `trade_operation_audit_log`。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
```

结果：

- 后端全模块编译通过。

## 当前边界

当前审计记录是同步写库，但使用 `recordSafely` 不影响主业务成功返回。

后续可增强：

- 审计失败写本地消息表异步补偿。
- 增加审计查询接口。
- 增加管理端审计列表。
- 把 `request_content/response_content` 做敏感字段脱敏。
- 对 Agent 写操作增加 `agentSessionId`、`toolName`、`confirmId`。

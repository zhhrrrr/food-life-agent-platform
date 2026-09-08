# 开发日志-081-trade运营审计查询接口

## 本次目标

上一阶段已经让关键写操作能够写入 `trade_operation_audit_log`。这一步继续补齐运营排障能力：提供审计日志查询接口，让运营和开发可以按 traceId、操作人、操作类型、业务对象定位问题。

## 新增接口

```text
GET /api/trade/operations/audit-logs
```

查询参数：

| 参数 | 说明 |
| --- | --- |
| traceId | 按全链路 traceId 查询 |
| operatorId | 按操作人查询 |
| operationType | 按操作类型查询 |
| bizType | 按业务对象类型查询 |
| bizId | 按业务对象 ID 查询 |
| limit | 返回条数，默认 50，最大 200 |

权限：

- 该接口位于 `/api/trade/operations/**`
- 已被 `food.auth.role-access` 配置保护
- 只有 `ADMIN` 或 `OPERATOR` 可访问

## DDD 分层改造

### api 层

新增：

- `food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/OperationAuditLogResponseDTO.java`
- `food-trade-service/food-trade-api/src/main/java/com/foodlife/trade/api/dto/OperationAuditLogListResponseDTO.java`

职责：

- 定义 HTTP 返回结构
- 不暴露 infrastructure PO

### domain 层

新增：

- `food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/audit/model/OperationAuditQueryEntity.java`

调整：

- `IOperationAuditRepository` 新增 `list(query)`
- `OperationAuditDomainService` 新增 `list(query)`

职责：

- 处理默认 limit 和最大 limit
- 保持查询能力仍经过领域服务，而不是 Controller 直接查 Mapper

### infrastructure 层

调整：

- `food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OperationAuditRepository.java`

新增：

- 按条件动态查询
- 按 `id desc` 返回最新记录
- 使用 MyBatis-Plus `LambdaQueryWrapper`

### trigger 层

新增：

- `food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OperationAuditController.java`

职责：

- 接收查询参数
- 构造领域查询对象
- 将领域实体转换为 API DTO

## 使用示例

按订单查询：

```text
GET /api/trade/operations/audit-logs?bizType=ORDER&bizId=10001
```

按 traceId 查询：

```text
GET /api/trade/operations/audit-logs?traceId=abc123
```

按操作类型查询最近 20 条：

```text
GET /api/trade/operations/audit-logs?operationType=OPERATION_REFUND_CONFIRM&limit=20
```

## 对项目的价值

现在审计链路从“只写不查”升级为“可写、可查、可排障”：

- 线上退款异常可以按订单 ID 查。
- 支付回调异常可以按 traceId 查。
- 运营库存调整可以按 operatorId 查。
- 后续管理端可以直接接这个接口。
- 后续 Agent 写操作也可以复用同一套审计模型。

## 验证

已执行：

```bash
mvn -q -DskipTests compile
```

结果：

- 后端全模块编译通过。

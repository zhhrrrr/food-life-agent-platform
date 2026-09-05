# 开发日志-068-Seata链路正式化为运营库存调整

## 本次目标

把原来的 `SeataDemoController` 从“技术 Demo”升级成正式业务能力：运营库存调整。

这次没有把 Seata 套进普通下单、秒杀、拼团主链路。设计判断仍然保持不变：

- 高并发交易链路：本地事务 + RabbitMQ + 幂等 + 补偿。
- 低并发强一致运营链路：可以使用 Seata AT。

## 一、接口正式化

原接口：

```text
POST /api/trade/demo/seata/package-stock-adjust
```

新接口：

```text
POST /api/trade/operations/package-stock-adjustments
```

对应文件：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OperationStockAdjustmentController.java
```

含义变化：

- 原来表达的是“Seata 示例”。
- 现在表达的是“运营侧库存调整业务”。
- Seata 只作为底层事务治理技术，不暴露到 API 路径里。

## 二、应用服务正式化

对应文件：

```text
food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/app/OperationStockAdjustmentApplicationService.java
```

保留：

```java
@GlobalTransactional(name = "food-operation-package-stock-adjust", rollbackFor = Exception.class)
```

说明：

- trade-service 仍然是 Seata TM，全局事务发起方。
- 事务名称从 demo 语义改成正式业务语义。
- 业务代码不依赖 Seata API，只在应用服务边界声明全局事务。

## 三、领域层正式化

领域包从：

```text
com.foodlife.trade.domain.order.distributedtx
```

迁移到：

```text
com.foodlife.trade.domain.order.operation
```

核心文件：

```text
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/operation/service/OperationStockAdjustmentService.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/operation/repository/IOperationStockAdjustmentRepository.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/operation/model/OperationPackageStockAdjustCommand.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/operation/model/OperationPackageStockAdjustResult.java
food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/operation/model/OperationPackageStockAdjustLog.java
```

领域服务仍然负责：

1. 校验操作人、套餐、调整数量。
2. 生成或规范化 `operationId`。
3. 根据 `operationId` 做幂等判断。
4. 写入运营库存调整审计日志，状态 `PROCESSING`。
5. 调 business-service 内部库存调整接口。
6. 成功后更新审计日志为 `SUCCESS`。

## 四、基础设施正式化

原表：

```text
trade_distributed_tx_demo_log
```

新表：

```text
trade_operation_stock_adjust_log
```

对应文件：

```text
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/dao/po/OperationStockAdjustLogPO.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/dao/IOperationStockAdjustLogMapper.java
food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/repository/OperationStockAdjustmentRepository.java
```

新表定位：

- 不是 Seata 系统表。
- 是 trade-service 的正式业务审计表。
- 用于记录运营人员调整套餐库存的操作过程和最终状态。

## 五、数据库迁移

更新完整建库脚本：

```text
docs/sql/food_trade_db.sql
```

重命名原 066 脚本：

```text
docs/sql/food_trade_db_migration_066_operation_stock_adjustment.sql
```

新增兼容迁移：

```text
docs/sql/food_trade_db_migration_068_operation_stock_adjustment_formalize.sql
```

兼容迁移做两件事：

1. 创建正式表 `trade_operation_stock_adjust_log`。
2. 如果本地已存在旧表 `trade_distributed_tx_demo_log`，把旧数据 `INSERT IGNORE` 迁到正式表。

本地已执行：

```text
mysql -uroot -proot < docs/sql/food_trade_db_migration_068_operation_stock_adjustment_formalize.sql
```

## 六、调用链

正式业务流程：

```text
运营/登录用户
 -> Gateway 鉴权
 -> trade-service /api/trade/operations/package-stock-adjustments
 -> OperationStockAdjustmentApplicationService 开启 Seata 全局事务
 -> OperationStockAdjustmentService 写 trade_operation_stock_adjust_log PROCESSING
 -> BusinessPackagePort 通过 Feign 调 business-service 内部接口
 -> business-service /api/internal/package/{packageId}/stock/adjust
 -> PackageDomainService.adjustPackageStock
 -> PackageRepository 修改 meal_package.stock
 -> PackageRepository 写 package_stock_change_record
 -> trade-service 标记 trade_operation_stock_adjust_log SUCCESS
```

如果中间异常，并且 `SEATA_ENABLED=true` 且 Seata Server 正常运行：

```text
trade_operation_stock_adjust_log 回滚
meal_package.stock 回滚
package_stock_change_record 回滚
```

## 七、验证脚本

原脚本：

```text
scripts/smoke-seata-demo.ps1
```

新脚本：

```text
scripts/smoke-operation-stock-adjustment.ps1
```

同时更新：

```text
scripts/smoke-auth-internal-security.ps1
```

认证安全冒烟继续验证：trade-service 可以通过 Feign 内部密钥调用 business-service 内部库存调整接口。

## 八、面试说法

可以这样讲：

> 项目里没有把 Seata 滥用在下单、秒杀、拼团这些高并发链路上。核心交易链路主要采用本地事务、MQ 最终一致性、幂等和补偿。Seata 被放在运营库存调整这种低并发、人工触发、跨库强一致要求明确的场景里，用 AT 模式降低业务侵入，同时通过 undo_log 保证异常时可以回滚 trade 和 business 两边的数据。

# 数据记录-Flyway迁移治理

## 当前数据库归属

| 服务 | 数据库 | Flyway 迁移目录 | 初始迁移文件 |
| --- | --- | --- | --- |
| food-user-service | food_user_db | `food-user-service/food-user-app/src/main/resources/db/migration` | `V1__init_user_schema.sql` |
| food-business-service | food_business_db | `food-business-service/food-business-app/src/main/resources/db/migration` | `V1__init_business_schema.sql` |
| food-trade-service | food_trade_db | `food-trade-service/food-trade-app/src/main/resources/db/migration` | `V1__init_trade_schema.sql` |

## Flyway 运行配置

| 配置项 | 当前值 | 说明 |
| --- | --- | --- |
| `spring.flyway.enabled` | `${FLYWAY_ENABLED:true}` | 默认启用 Flyway，可通过环境变量临时关闭 |
| `spring.flyway.baseline-on-migrate` | `true` | 兼容已有非空数据库接入 Flyway |
| `spring.flyway.validate-on-migrate` | `true` | 启动时校验已执行迁移是否被篡改 |
| `spring.flyway.locations` | `classpath:db/migration` | 只扫描当前服务 app 包里的迁移脚本 |
| datasource URL | 包含 `createDatabaseIfNotExist=true` | 本地 MySQL 可自动创建 schema |

## Flyway 自动生成的表

| 表名 | 所在库 | 作用 |
| --- | --- | --- |
| `flyway_schema_history` | 每个服务库各一张 | 记录迁移版本、脚本名、校验和、执行时间、执行状态 |

## 现有业务表来源

| 数据库 | 业务表来源文件 | 运行时迁移文件 |
| --- | --- | --- |
| `food_user_db` | `docs/sql/food_user_db.sql` | `food-user-service/food-user-app/src/main/resources/db/migration/V1__init_user_schema.sql` |
| `food_business_db` | `docs/sql/food_business_db.sql` | `food-business-service/food-business-app/src/main/resources/db/migration/V1__init_business_schema.sql` |
| `food_trade_db` | `docs/sql/food_trade_db.sql` | `food-trade-service/food-trade-app/src/main/resources/db/migration/V1__init_trade_schema.sql` |

## 后续新增表的规则

新增数据库结构时，不直接修改已经发布过的 `V1` 文件。

推荐新增：

```text
V2__add_xxx_table.sql
V3__alter_xxx_add_column.sql
V4__create_xxx_index.sql
```

原因：

- Flyway 已执行版本会记录校验和，修改历史迁移会导致校验失败。
- 增量迁移更贴近真实发布流程。
- 线上数据库结构可追溯。

## 验证入口

```powershell
.\scripts\verify-database-migrations.ps1
.\scripts\verify-company-readiness.ps1 -SkipRuntimeSmoke
```


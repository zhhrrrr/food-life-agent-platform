# 开发日志-088-Flyway数据库迁移治理

## 背景

前面项目已经拆成 user、business、trade 等微服务，每个服务也对应独立数据库。但表结构主要依赖 `docs/sql` 下的人工初始化脚本，这种方式适合学习和本地快速导入，不适合公司项目里的持续迭代。

本次改造目标是把数据库结构纳入服务版本管理：服务启动时由 Flyway 自动检查和执行本服务的迁移脚本。

## 本次改造

### 1. 三个业务服务 app 模块接入 Flyway

- `food-user-service/food-user-app/pom.xml`
- `food-business-service/food-business-app/pom.xml`
- `food-trade-service/food-trade-app/pom.xml`

新增 `org.flywaydb:flyway-core`，由 Spring Boot 自动装配 Flyway。

### 2. 新增服务内迁移脚本

- `food-user-service/food-user-app/src/main/resources/db/migration/V1__init_user_schema.sql`
- `food-business-service/food-business-app/src/main/resources/db/migration/V1__init_business_schema.sql`
- `food-trade-service/food-trade-app/src/main/resources/db/migration/V1__init_trade_schema.sql`

迁移脚本来源于现有 `docs/sql/food_*_db.sql`，但去掉了 `CREATE DATABASE` 和 `USE`。

原因：

- Flyway 只管理当前 datasource 指向的 schema。
- 每个微服务只迁移自己的库。
- 避免某个服务启动时误操作其他服务数据库。

### 3. local 和 Nacos 配置都开启 Flyway

本地配置：

- `food-user-service/food-user-app/src/main/resources/application-local.yml`
- `food-business-service/food-business-app/src/main/resources/application-local.yml`
- `food-trade-service/food-trade-app/src/main/resources/application-local.yml`

Nacos 配置源文件：

- `deploy/nacos/configs/food-user-service.yaml`
- `deploy/nacos/configs/food-business-service.yaml`
- `deploy/nacos/configs/food-trade-service.yaml`

新增配置：

```yaml
spring:
  flyway:
    enabled: ${FLYWAY_ENABLED:true}
    baseline-on-migrate: true
    validate-on-migrate: true
    locations: classpath:db/migration
```

### 4. 本地 MySQL 支持自动建库

三个服务的 datasource URL 增加：

```text
createDatabaseIfNotExist=true
```

作用是降低本地启动门槛：MySQL 服务存在、账号权限足够时，应用连接数据库时可以自动创建对应 schema，之后 Flyway 再执行建表迁移。

### 5. 新增数据库迁移治理验证脚本

新增：

- `scripts/verify-database-migrations.ps1`

并接入：

- `scripts/verify-company-readiness.ps1`

验证内容：

- 三个 app 模块必须依赖 Flyway。
- 三个服务必须存在 `V1__init_*_schema.sql`。
- Flyway 迁移脚本不能包含 `CREATE DATABASE` 或 `USE`。
- local 和 Nacos 配置必须默认启用 Flyway。
- datasource 必须包含 `createDatabaseIfNotExist=true`。

## 启动后的真实效果

服务启动时：

1. Spring Boot 创建 datasource。
2. Flyway 扫描 `classpath:db/migration`。
3. Flyway 检查当前库的 `flyway_schema_history`。
4. 没执行过的迁移会按版本执行。
5. 执行成功后记录版本。
6. 后续服务重启不会重复执行已经成功的迁移。

## 和 docs/sql 的关系

`docs/sql` 继续保留，作为：

- 学习说明
- 人工初始化参考
- 数据结构总览
- 面试讲解材料

服务真正启动时，以服务内 `db/migration` 为准。

## 面试可讲点

- 为什么微服务数据库迁移要跟服务代码走？
- Flyway 的版本号怎么设计？
- 为什么迁移脚本不应该写 `USE database`？
- `baseline-on-migrate` 解决什么问题？
- 已有库接入 Flyway 怎么处理历史表？
- 为什么每个服务维护自己的迁移，而不是所有服务共用一个总 SQL？


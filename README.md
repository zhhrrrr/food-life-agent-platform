# food-life-agent-platform

美食生活业务 Agent 项目。

## Current Stage

第一阶段先开发：

```text
food-auth-starter
food-user-service
food-business-service
```

当前目标：

```text
1. 跑通黑马点评风格的手机号验证码登录
2. 使用 Redis Token 和 UserHolder 管理登录态
3. 使用 food-auth-starter 复用认证拦截器和 Feign Token 透传
4. 使用 food-user-service 管理用户主数据
5. 使用 food-business-service 提供店铺、分类、套餐查询能力
```

后续开发：

```text
food-trade-service
food-gateway-service
python-agent-service
```

## Local Databases

本地开发默认使用：

```text
MySQL localhost:3306 root/root
Redis localhost:6379
```

SQL 脚本：

```text
docs/sql/food_user_db.sql
docs/sql/food_business_db.sql
```

# food-life-agent-platform

美食生活业务 Agent 项目。

## Current Stage

当前先开发不涉及 Agent 的业务服务，Agent 和 Python Runtime 暂缓。

当前核心服务：

```text
food-auth-starter
food-user-service
food-business-service
food-trade-service
food-life-agent-web
```

当前已经完成的主流程：

```text
1. 黑马点评风格手机号验证码登录
2. Redis Token 和 UserHolder 登录态
3. food-auth-starter 复用认证拦截器和 Token 透传
4. 用户主数据、关注关系、用户资料、个人主页
5. 店铺、分类、套餐、评价、收藏、店铺主页聚合
6. 普通购买、拼团、秒杀
7. 模拟支付、支付单、支付回调、超时关单
8. 取消、退款、到店核销
9. 订单详情、订单列表、交易链路查询
10. 面向用户的美食拼团 Agent 前端
```

后续开发：

```text
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
docs/sql/food_trade_db.sql
```

## Local Services

本地端口：

```text
food-user-service      http://localhost:8101
food-business-service  http://localhost:8201
food-trade-service     http://localhost:8301
```

启动三服务：

```powershell
.\scripts\start-local-services.ps1
```

重新打包并重启三服务：

```powershell
.\scripts\start-local-services.ps1 -Rebuild
```

停止三服务：

```powershell
.\scripts\stop-local-services.ps1
```

网关前冒烟检查：

```powershell
.\scripts\smoke-before-gateway.ps1
```

带登录 Token 的冒烟检查：

```powershell
.\scripts\smoke-before-gateway.ps1 -Token "{token}"
```

## Local Frontend

前端工程：

```text
food-life-agent-web
```

启动：

```powershell
cd food-life-agent-web
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

前端通过 Vite 代理联调本地后端：

```text
/user-api      -> http://localhost:8101/api/user
/business-api  -> http://localhost:8201/api
/trade-api     -> http://localhost:8301/api/trade
```

## Before Gateway

当前还没有实现 `food-gateway-service`。

网关开始前，三服务已经可以独立启动和验证。后续网关只负责统一入口、路由、鉴权透传、跨域和限流，业务逻辑继续留在各自微服务内。

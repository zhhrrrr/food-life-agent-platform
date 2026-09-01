# 开发日志-053-美食拼团 Agent 前端联调

## 1. 本次目标

本次新增真正可运行、可和后端联调的用户端前端应用：

```text
food-life-agent-web
```

技术栈：

```text
Vue 3
TypeScript
Vite
Vue Router
Pinia
Axios
Element Plus
@element-plus/icons-vue
```

## 2. 工程结构

```text
src/
├── api
├── assets
├── components
├── layouts
├── router
├── stores
├── styles
├── types
├── utils
└── views
```

本次没有把逻辑堆到单个页面里，接口、类型、状态、布局、组件和页面已经分层。

## 3. 当前页面

登录页：

```text
src/views/LoginView.vue
```

首页店铺流：

```text
src/views/HomeView.vue
```

店铺详情与套餐下单：

```text
src/views/ShopDetailView.vue
```

我的订单：

```text
src/views/OrdersView.vue
```

应用布局：

```text
src/layouts/AppLayout.vue
```

## 4. 当前组件

店铺卡片：

```text
src/components/ShopCard.vue
```

套餐卡片：

```text
src/components/PackageCard.vue
```

订单卡片：

```text
src/components/OrderCard.vue
```

Agent 面板：

```text
src/components/AgentDock.vue
```

说明：

```text
AgentDock 当前是前端侧交互入口，后端 Agent 和 Python Runtime 暂未接入。
后续接 Agent 时，可以在该组件下接 food-agent-service 或 python-agent-runtime。
```

## 5. 后端代理

配置文件：

```text
food-life-agent-web/vite.config.ts
```

代理规则：

```text
/user-api      -> http://localhost:8101/api/user
/business-api  -> http://localhost:8201/api
/trade-api     -> http://localhost:8301/api/trade
```

这样前端可以直接通过 Vite 代理联调三个后端微服务，后续接网关时也方便切换。

## 6. 已接入接口

用户服务：

```text
POST /user-api/code
POST /user-api/login
GET  /user-api/me
```

业务服务：

```text
GET    /business-api/shop-category/list
GET    /business-api/shop/of/category
GET    /business-api/shop/of/name
GET    /business-api/shop-homepage/{shopId}
POST   /business-api/favorites/shops/{shopId}
DELETE /business-api/favorites/shops/{shopId}
```

交易服务：

```text
POST /trade-api/orders/normal
POST /trade-api/orders/group-buy
POST /trade-api/orders/seckill
GET  /trade-api/seckill/activities
GET  /trade-api/orders
```

## 7. 本地验证

构建验证：

```powershell
npm run build
```

结果：

```text
vue-tsc -b 通过
vite build 通过
```

说明：

```text
Vite 有 chunk size warning，主要来自 Element Plus 依赖体积，不影响运行。
后续可以做按需组件导入和分包优化。
```

代理联调验证：

```text
http://localhost:5173/business-api/shop-category/list
http://localhost:5173/user-api/code
http://localhost:5173/user-api/login
http://localhost:5173/user-api/me
http://localhost:5173/business-api/shop-homepage/1
http://localhost:5173/trade-api/orders?pageSize=3
```

验证数据：

```text
phone = 13900000959
user_id = 55
```

浏览器联调验证：

```text
1. 打开 http://localhost:5173
2. 未登录自动进入 /login
3. 写入登录 Token 后进入首页
4. 首页展示分类、店铺卡片、Agent 面板
5. 点击店铺进入 /shops/1
6. 店铺详情展示套餐、收藏、评价、下单按钮
7. 点击普通购买创建订单
8. 自动跳转 /orders
9. 订单中心展示新创建订单
```

本次浏览器验证创建订单：

```text
orderNo = NO178822621225355
orderStatus = WAIT_PAY
tradeType = NORMAL
```

## 8. 当前边界

已完成：

```text
前端工程搭建
用户端视觉风格
登录页
首页店铺流
店铺详情聚合
普通购买下单
拼团下单入口
秒杀下单入口
我的订单筛选入口
Vite 代理联调
生产构建验证
浏览器级冒烟验证
```

未完成：

```text
真实 Agent 对话接口
支付页面
核销码页面
评价发布页面
用户资料编辑页面
前端自动化测试用例
Element Plus 按需导入优化
```

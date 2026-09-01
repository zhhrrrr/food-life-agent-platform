# 开发日志-055-OpenFeign跨服务调用改造

## 1. 本次目标

本次在 Nacos 基线之后继续推进微服务调用改造：

```text
RestTemplate 固定 URL 调用
  -> OpenFeign 服务名调用
```

本次仍然不改变领域层接口和核心业务流程，只替换 infrastructure 层的远程调用实现。

## 2. 改造前的问题

改造前有两处跨服务调用：

```text
food-trade-service    -> food-business-service
food-business-service -> food-trade-service
```

原实现依赖：

```text
RestTemplate
http://localhost:8201
http://localhost:8301
Response.class + Map 手动转换
手动 Header 透传
```

这些方式可以本地跑通，但不适合继续做注册中心、负载均衡、熔断降级和链路追踪。

## 3. 新增服务契约 DTO

在 `food-business-api` 新增套餐相关 DTO：

```text
PackageTradeSnapshotResponseDTO
PackageStockChangeRecordResponseDTO
PackageStockChangeResponseDTO
```

这样 trade-service 调 business-service 时依赖的是 business-service 的 api 契约，不再依赖对方 domain 模型。

## 4. 新增 Feign Client

新增：

```text
food-trade-service/food-trade-infrastructure/.../feign/BusinessPackageClient.java
food-business-service/food-business-infrastructure/.../feign/TradeOrderClient.java
```

调用方向：

```text
BusinessPackageClient:
food-trade-service -> food-business-service

TradeOrderClient:
food-business-service -> food-trade-service
```

Feign Client 使用服务名：

```text
food-business-service
food-trade-service
```

## 5. Infrastructure Port 改造

`BusinessPackagePort`：

```text
RestTemplate -> BusinessPackageClient
Map 转换 -> DTO 转领域模型
```

负责：

```text
查询套餐交易快照
查询库存变更记录
预占套餐库存
释放套餐库存
确认销量
回滚销量
```

`TradeOrderPort`：

```text
RestTemplate -> TradeOrderClient
手动 Header 透传 -> FeignAuthRequestInterceptor 自动透传
Map 转换 -> DTO 转领域模型
```

负责评价业务创建前的订单校验。

## 6. 启动类开启 Feign

新增：

```text
@EnableFeignClients
```

位置：

```text
BusinessApplication
TradeApplication
```

同时删除这两个应用里原来的 `RestTemplate` Bean。

## 7. 本地服务发现配置

为了不破坏默认本地开发模式，新增 SimpleDiscoveryClient 本地实例：

```text
food-business-service -> http://localhost:8201
food-trade-service    -> http://localhost:8301
```

这样没有启动 Nacos 时，Feign 仍然可以按服务名完成本地调用。

## 8. 验证结果

编译验证：

```powershell
mvn -pl "food-business-service/food-business-app,food-trade-service/food-trade-app" -am package -DskipTests
```

结果：

```text
BUILD SUCCESS
```

本地启动验证：

```powershell
.\scripts\start-local-services.ps1 -Restart
```

结果：

```text
food-user-service healthy on port 8101
food-business-service healthy on port 8201
food-trade-service healthy on port 8301
```

基础冒烟：

```powershell
.\scripts\smoke-before-gateway.ps1
```

结果：

```text
Pre-gateway smoke check completed.
```

完整 Feign 业务链路：

```text
登录成功
普通购买下单成功，orderId=72
模拟支付成功
模拟核销成功
创建评价成功，reviewId=3
```

## 9. 面试点对应

本次可以讲：

```text
1. Feign 和 RestTemplate 的区别
2. 声明式 HTTP 调用
3. 服务名调用和注册中心发现
4. 客户端负载均衡 LoadBalancer
5. Feign 请求拦截器实现 Token 透传
6. DTO 契约和领域模型隔离
7. 远程调用失败如何处理
8. 为什么不能在 domain 层直接写 HTTP 调用
```

## 10. 下一步

下一步建议做 `food-gateway-service`：

```text
1. 统一入口
2. 路由转发
3. 跨域处理
4. Token 透传
5. 网关白名单
6. 网关级限流预留
```

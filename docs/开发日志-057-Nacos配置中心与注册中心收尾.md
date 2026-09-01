# 开发日志-057-Nacos配置中心与注册中心收尾

## 1. 本次目标

本次把 Nacos 相关事项收尾到可运行、可验证、可复现的状态。

完成范围：

```text
1. Nacos Server 本地 standalone 模式运行
2. 三个微服务注册到 Nacos
3. Nacos 配置中心 DataId 落仓库
4. 配置一键发布到 Nacos
5. 配置一键校验
6. 服务注册一键校验
7. Nacos 模式重新启动三服务
8. Nacos + OpenFeign 业务链路验证
```

## 2. 新增 Nacos 配置源文件

新增目录：

```text
deploy/nacos/configs
```

这个目录用于保存 Nacos 配置中心的 Git 基线，避免只在 Nacos 控制台手工维护配置。

新增 DataId 源文件：

```text
food-common.yaml
food-user-service.yaml
food-business-service.yaml
food-trade-service.yaml
```

## 3. 配置拆分说明

`food-common.yaml`：

```text
Redis 公共配置
Feign 超时配置
MyBatis-Plus 公共配置
认证公共配置
```

`food-user-service.yaml`：

```text
user-service 端口
user-service 数据库
用户仓储类型
登录白名单
```

`food-business-service.yaml`：

```text
business-service 端口
business-service 数据库
本地 simple discovery 兜底
店铺/套餐接口白名单
```

`food-trade-service.yaml`：

```text
trade-service 端口
trade-service 数据库
本地 simple discovery 兜底
普通订单、拼团、秒杀、支付、优惠券定时任务配置
支付回调白名单
```

## 4. 新增脚本

新增：

```text
scripts/publish-nacos-configs.ps1
scripts/verify-nacos-configs.ps1
scripts/verify-nacos-services.ps1
```

`publish-nacos-configs.ps1`：

```text
读取 deploy/nacos/configs/*.yaml
通过 Nacos OpenAPI 发布到配置中心
默认 group=FOOD_LIFE_AGENT
```

`verify-nacos-configs.ps1`：

```text
从 Nacos 配置中心读取每一个 DataId
确认配置存在且内容非空
```

`verify-nacos-services.ps1`：

```text
从 Nacos 注册中心查询服务列表
确认 user/business/trade 三个服务存在
确认每个服务至少有一个 healthy=true 的实例
```

同时增强：

```text
scripts/start-local-services-nacos.ps1
```

启动三个微服务前会先检查 Nacos 是否可访问，避免 Nacos 未启动时继续启动业务服务。

## 5. 配置发布验证

执行：

```powershell
.\scripts\publish-nacos-configs.ps1
.\scripts\verify-nacos-configs.ps1
```

发布成功：

```text
food-business-service.yaml
food-common.yaml
food-trade-service.yaml
food-user-service.yaml
```

校验成功：

```text
food-business-service.yaml length=647
food-common.yaml length=420
food-trade-service.yaml length=1304
food-user-service.yaml length=528
```

## 6. Nacos 模式启动验证

执行：

```powershell
.\scripts\start-local-services-nacos.ps1 -Rebuild -Restart
```

结果：

```text
BUILD SUCCESS
food-user-service healthy on port 8101
food-business-service healthy on port 8201
food-trade-service healthy on port 8301
All local services are ready.
```

## 7. 服务注册验证

执行：

```powershell
.\scripts\verify-nacos-services.ps1
```

结果：

```text
food-user-service      -> 100.67.58.243:8101 healthy=True
food-business-service  -> 100.67.58.243:8201 healthy=True
food-trade-service     -> 100.67.58.243:8301 healthy=True
```

## 8. 配置中心加载证据

服务启动日志中可以看到 Nacos 配置中心被加载：

```text
NacosConfigBootstrapConfiguration matched
Located property source: bootstrapProperties-food-user-service.yaml,FOOD_LIFE_AGENT
Located property source: bootstrapProperties-food-common.yaml,FOOD_LIFE_AGENT
```

服务注册日志中可以看到注册完成：

```text
NacosServiceRegistry: nacos registry, FOOD_LIFE_AGENT food-user-service 100.67.58.243:8101 register finished
```

## 9. 业务链路验证

执行登录和普通购买下单：

```text
登录成功
普通购买下单成功
orderId=75
orderNo=NO178825369012756
orderStatus=WAIT_PAY
```

这条链路证明：

```text
trade-service
  -> OpenFeign
  -> Nacos 服务发现
  -> business-service
  -> 查询套餐快照
  -> 预占套餐库存
```

## 10. 当前 Nacos 完成状态

已完成：

```text
Nacos Server 本地安装
Nacos Server 本地启动
Nacos 控制台访问
客户端 bootstrap.yml
服务注册 discovery
配置中心 config
配置 DataId 源文件
配置发布脚本
配置校验脚本
服务注册校验脚本
Nacos 模式三服务启动
Nacos + OpenFeign 业务验证
```

暂不做：

```text
Nacos 集群部署
Nacos MySQL 持久化
Nacos 权限认证加固
多环境 namespace 完整拆分
```

这些属于生产环境部署层，后续可以单独作为部署专题继续扩展。

## 11. 面试点对应

本次可以讲：

```text
1. Nacos Server 和 Client 的区别
2. 服务注册与发现流程
3. 配置中心 DataId / Group / Namespace
4. bootstrap.yml 为什么比 application.yml 更早加载
5. 配置中心和本地配置的关系
6. OpenFeign 如何通过服务名调用
7. 本地开发如何兼容注册中心不可用
8. 为什么配置中心也要有 Git 基线
9. 如何验证服务是否真的注册进 Nacos
10. 如何验证服务是否真的读取了 Nacos 配置
```

## 12. 下一步

Nacos 已经收尾完成。下一步适合进入：

```text
food-gateway-service
```

重点做：

```text
统一入口
路由转发
跨域处理
Token 透传
白名单
网关日志
网关限流预留
```

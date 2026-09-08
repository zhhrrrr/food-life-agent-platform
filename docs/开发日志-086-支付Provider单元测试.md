# 开发日志-086-支付Provider单元测试

## 本次目标

给支付通道抽象补第一组单元测试，让 CI 的 `mvn test` 不再只是空跑。

## 新增测试

文件：

```text
food-trade-service/food-trade-infrastructure/src/test/java/com/foodlife/trade/infrastructure/payment/LocalPaymentProviderTest.java
```

覆盖点：

- `PaymentProviderRouter` 可以按 channel 路由到 `LocalPaymentProvider`
- channel 支持大小写和空格归一化
- 不支持的支付渠道会被拒绝
- 本地支付准备命令为空会被拒绝
- 支付回调金额不一致会被拒绝

## 修改依赖

`food-trade-infrastructure/pom.xml` 新增：

```xml
spring-boot-starter-test
```

作用：

- JUnit Jupiter
- AssertJ
- 后续可以继续扩展 Mockito 单测

## 验证

已执行：

```bash
mvn -q test
```

结果：

- Maven 测试通过
- `LocalPaymentProviderTest` 已纳入 CI 的 `mvn test` 范围

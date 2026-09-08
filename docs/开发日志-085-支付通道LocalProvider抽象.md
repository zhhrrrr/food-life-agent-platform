# 开发日志-085-支付通道LocalProvider抽象

## 本次目标

把支付链路从“mock 命名”收敛成明确的支付通道抽象。

当前不接真实微信/支付宝，但代码结构按公司项目常见方式预留：

```text
PaymentOrderService
  -> PaymentProviderRouter
  -> IPaymentProvider
  -> LocalPaymentProvider
```

后续接真实支付时，只需要新增 `WechatPayProvider`、`AlipayProvider` 这类适配器，再在配置里放开对应 channel。

## 本次新增

- `PaymentChannelConstants`
  - 定义当前本地支付渠道 `LOCAL_PAY`
- `IPaymentProvider`
  - 领域层支付渠道端口
  - 负责准备支付校验、支付成功回调验真
- `PaymentProviderRouter`
  - 根据 channel 路由到具体支付 Provider
- `LocalPaymentProvider`
  - 当前本地支付通道实现
  - 负责本地开发和联调

## 本次修改

- `PaymentOrderService`
  - 准备支付时通过 `PaymentProviderRouter` 校验 channel
  - 默认 channel 从 `MOCK_PAY` 改为 `LOCAL_PAY`
  - 支付回调金额校验迁移到 Provider
- `PaymentController`
  - 新增正式入口 `POST /api/trade/pay/callback/local`
  - 旧入口 `POST /api/trade/pay/callback/mock` 暂时保留兼容
  - 审计类型从 `PAYMENT_CALLBACK_MOCK` 改为 `PAYMENT_CALLBACK_LOCAL`
- `OrderController`
  - 新增正式入口 `POST /api/trade/orders/{orderId}/pay/local`
  - 新增正式入口 `POST /api/trade/orders/{orderId}/use/local`
  - 旧 mock 路径暂时保留兼容
  - 默认支付 channel 改为 `LOCAL_PAY`
- 前端
  - 准备支付默认 channel 改为 `LOCAL_PAY`
  - 支付回调改走 `/trade-api/pay/callback/local`
  - 核销改走 `/trade-api/orders/{orderId}/use/local`
- `smoke-rabbitmq-events.ps1`
  - 支付回调和核销改走 local 正式入口

## 为什么这样做

这不是接真实支付，但它把“支付能力边界”抽出来了：

- Controller 不直接关心某个支付渠道的验签/验额细节
- 领域服务通过 Provider 端口处理渠道差异
- 本地联调使用 `LOCAL_PAY`，语义比 `MOCK_PAY` 更清楚
- 未来接真实渠道时不需要推翻当前支付单、订单结算和 MQ 事件链路

## 验证

已执行：

```bash
mvn -q -DskipTests compile
npm run build
powershell -ExecutionPolicy Bypass -File scripts/verify-microservice-boundaries.ps1
```

结果：

- 后端全模块编译通过
- 前端构建通过
- 微服务边界验证通过

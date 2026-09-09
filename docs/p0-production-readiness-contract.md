# P0 Production Readiness Contract

This document is the ASCII-path companion for Windows verification scripts. The Chinese learning note is:

```text
docs/P0生产化联调与接口契约.md
```

## Scope

P0 does not connect to real Alipay or WeChat Pay. The goal is to make local payment, refund, role permission, error code governance, and end-to-end business smoke verification work as a production-like local skeleton.

## Local Payment Contract

Callback body:

```json
{
  "payOrderNo": "PAY...",
  "outTradeNo": "OUT...",
  "payAmount": 16800,
  "signType": "SHA256",
  "signature": "sha256 hex"
}
```

Signature plain text:

```text
payOrderNo|outTradeNo|payAmount|LOCAL_PAY|local-payment-secret
```

Config key:

```text
food.payment.local.callback-secret
```

## Refund Contract

Refund creates or reuses a `refund_order` record. One `pay_order_no` maps to one refund order through `uk_pay_order_no`.

Core flow:

```text
validate order status
query payment order
create/reuse refund_order
call PaymentProvider.refund
mark refund_order SUCCESS
mark payment_order REFUNDED
publish payment.refunded event
release coupon and stock side effects
```

## Role Contract

Login still uses the Dianping-style Redis token. The role value written into the token comes from `food_user_db.user_role`.

## Error Codes

```text
ORDER_409
PAYMENT_401
PAYMENT_502
```

## Smoke Test

```powershell
.\scripts\verify-p0-readiness.ps1
.\scripts\smoke-p0-business-flow.ps1
```


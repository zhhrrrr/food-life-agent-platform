const LOCAL_PAYMENT_SECRET = 'local-payment-secret'

export async function buildLocalPaymentSignature(payload: {
  payOrderNo: string
  outTradeNo: string
  payAmount: number
}) {
  const plainText = `${payload.payOrderNo}|${payload.outTradeNo}|${payload.payAmount}|LOCAL_PAY|${LOCAL_PAYMENT_SECRET}`
  const bytes = new TextEncoder().encode(plainText)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((item) => item.toString(16).padStart(2, '0'))
    .join('')
}


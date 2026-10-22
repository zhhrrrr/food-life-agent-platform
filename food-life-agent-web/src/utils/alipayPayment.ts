/** Read SDK fields as data and create a new form; never execute the returned HTML/scripts. */
export function createAlipayPaymentForm(html: string): HTMLFormElement {
  const parsed = new DOMParser().parseFromString(html, 'text/html')
  const source = parsed.querySelector('form')
  if (!source || source.method.toLowerCase() !== 'post') {
    throw new Error('未获取到有效的支付宝支付表单，请刷新后重试')
  }
  const action = new URL(source.getAttribute('action') || '')
  if (action.protocol !== 'https:' || action.hostname !== 'openapi-sandbox.dl.alipaydev.com'
    || action.pathname !== '/gateway.do' || action.port || action.username || action.password) {
    throw new Error('支付宝沙箱支付地址无效')
  }
  const inputs = Array.from(source.querySelectorAll('input[name]'))
  const fields = new Map(inputs.map(input => [(input as HTMLInputElement).name, (input as HTMLInputElement).value]))
  // The SDK splits protocol parameters and biz_content across URL and body.
  // Submit the same signed values together in POST: the sandbox page-pay route
  // can return 404 when method/sign are carried only in the URL query.
  const parameters = new Map([...action.searchParams, ...fields])
  if (parameters.get('method') !== 'alipay.trade.page.pay' || !parameters.get('sign') || !fields.get('biz_content')) {
    throw new Error('支付宝支付参数不完整，请刷新后重试')
  }
  const form = document.createElement('form')
  const charset = parameters.get('charset') || 'utf-8'
  if (charset.toLowerCase() !== 'utf-8') throw new Error('支付宝支付字符集无效')
  form.action = `${action.origin}${action.pathname}?charset=utf-8`
  form.method = 'POST'
  form.acceptCharset = 'utf-8'
  form.style.display = 'none'
  for (const [name, value] of parameters) {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    form.appendChild(input)
  }
  return form
}

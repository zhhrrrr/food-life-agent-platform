async (page) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.unroute('**/*')
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  const errors = []
  const calls = []
  const checks = []
  const screenshots = []
  const viewports = [1440, 1024, 768, 390, 320]
  page.on('pageerror', error => errors.push(error.message))
  const check = (condition, label) => { if (!condition) throw new Error(label); checks.push(label) }
  const origin = new URL(page.url()).origin
  async function layout(label, width) {
    await page.setViewportSize({ width, height: 900 })
    const overflow = await page.evaluate(() => ({ width: innerWidth, content: document.documentElement.scrollWidth,
      offenders: [...document.querySelectorAll('main *, .topbar')].filter(el => {
        const rect = el.getBoundingClientRect()
        return rect.width && rect.right > innerWidth + 2 && getComputedStyle(el).position !== 'fixed'
      }).slice(0, 5).map(el => el.className) }))
    check(overflow.content <= overflow.width + 1, `${label} fits ${width}px: ${JSON.stringify(overflow)}`)
  }
  async function capture(name) {
    await page.locator('.el-message').last().waitFor({ state: 'hidden', timeout: 10_000 })
    await page.evaluate(async () => {
      scrollTo(0, 0)
      await Promise.all([...document.images].map(img => { img.loading = 'eager'; return img.decode().catch(() => {}) }))
      await document.fonts.ready
    })
    const path = `output/playwright/${name}.png`
    await page.screenshot({ path, fullPage: true, animations: 'disabled' })
    screenshots.push(path)
  }
  async function contrast(selector, label) {
    const value = await page.locator(selector).first().evaluate(el => {
      const rgb = color => color.match(/[\d.]+/g).slice(0, 3).map(Number)
      const luminance = values => values.map(v => v / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
        .reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0)
      let surface = el
      while (surface.parentElement && getComputedStyle(surface).backgroundColor === 'rgba(0, 0, 0, 0)') surface = surface.parentElement
      const a = luminance(rgb(getComputedStyle(el).color)), b = luminance(rgb(getComputedStyle(surface).backgroundColor))
      return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
    })
    check(value >= 4.5, `${label} has readable contrast (${value.toFixed(2)}:1)`)
  }
  const confirmationId = '11111111111111111111111111111111'
  let run, disconnectConfirmation = true, expireOrders = false, expiredOrderRequests = 0, empty = false, role = 'OPERATOR'
  let pagedShops = false, pauseNextStream = false, pausedStream
  const pack = { id: 42, name: '双人火锅套餐', price: 6000, originalPrice: 8000, stock: 10, useRule: '预约到店', description: '双人套餐' }
  const shop = id => ({ id, name: `测试店铺${id}`, score: 45, comments: 12, sold: 280, avgPrice: 3000, area: '测试商圈', address: '测试地址', openHours: '10:00-22:00' })
  const order = { orderId: 101, orderNo: `ORDER101-${'long-order-number-'.repeat(5)}`, userId: 7, shopId: 1, packageId: 42, shopNameSnapshot: '测试店铺1', packageNameSnapshot: '双人火锅套餐', quantity: 1, payAmount: 6000, tradeType: 'NORMAL', orderStatus: 'WAIT_PAY', createTime: '2026-10-19T10:00:00' }
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname
    if (path === '/images/broken-fixture.jpg') return route.fulfill({ status: 404, body: 'fixture image not found' })
    if (!/^\/(user-api|business-api|trade-api|api\/agent)\//.test(path)) return route.continue()
    const body = request.method() === 'POST' && request.postData() ? JSON.parse(request.postData()) : null
    calls.push({ path, search: url.search, method: request.method(), body })
    const json = data => route.fulfill({ json: { code: '0000', message: 'success', data } })
    if (path === '/user-api/code') return json(true)
    if (path === '/user-api/login') return json({ token: 'browser-test-token' })
    if (path === '/user-api/me') return json({ id: 7, nickName: '测试用户', role })
    if (path === '/business-api/shop-category/list') return json([{ id: 1, name: '火锅' }, { id: 2, name: '烧烤' }])
    if (path.startsWith('/business-api/shop/of/')) return json(empty ? [] : pagedShops
      ? Number(url.searchParams.get('current') || 1) === 1 ? Array.from({ length: 10 }, (_, index) => shop(index + 1)) : [shop(11)]
      : [shop(1), shop(2)])
    if (path.startsWith('/business-api/shop-homepage/')) return json({ shop: shop(Number(path.split('/').at(-1))), packages: empty ? [] : [pack], favorite: false, reviewSummary: { latestReviews: empty ? [] : [{ reviewId: 1, score: 4.5, content: '食材新鲜，双人套餐分量刚好。', createTime: '2026-10-19T10:00:00' }] } })
    if (path.startsWith('/business-api/favorites/')) return json(true)
    if (path === '/trade-api/orders') {
      if (expireOrders) { expiredOrderRequests++; return route.fulfill({ status: 401, json: { message: '登录已过期' } }) }
      return json({ orders: empty ? [] : [order], hasMore: false })
    }
    if (path === '/trade-api/operations/orders') return json({ orders: empty ? [] : [order], hasMore: false })
    if (path === '/trade-api/seckill/activities') return json({ activities: [{ activityId: 3, packageId: 42, activityName: '限时秒杀', seckillPrice: 5000, activityStatus: 1, validStartTime: '2026-10-19T00:00:00', validEndTime: '2026-10-21T00:00:00', stock: 10, userTakeLimit: 1, canBuy: true }] })
    if (/^\/trade-api\/orders\/(normal|group-buy|seckill)$/.test(path) && request.method() === 'POST') {
      order.orderStatus = 'WAIT_PAY'; order.quantity = body.quantity
      order.tradeType = path.endsWith('/group-buy') ? 'GROUP_BUY' : path.endsWith('/seckill') ? 'SECKILL' : 'NORMAL'
      return json(order)
    }
    if (path === '/trade-api/orders/101/cancel') {
      order.orderStatus = 'CANCELED'
      return json({ orderId: order.orderId, orderNo: order.orderNo, orderStatus: order.orderStatus })
    }
    if (path.endsWith('/audit-logs')) return json({ logs: empty ? [] : [{ id: 1, operationType: 'PAYMENT_PREPARE', operationStatus: 'SUCCESS', operatorId: 7, operatorRole: 'OPERATOR', bizType: 'PAYMENT_ORDER', bizId: 'business-id-'.repeat(8), traceId: 'trace-id-'.repeat(10), remark: '创建支付预览', createTime: '2026-10-19T10:00:00', requestContent: '{"orderId":101}', responseContent: '{"status":"PREPARED"}' }] })
    if (path.endsWith('/mq/messages')) return json({ messages: empty ? [] : [{ id: 1, messageId: 'message-id-'.repeat(12), messageType: 'order.stock.occupied', bizId: 'ORDER101', messageStatus: 'FAILED', retryCount: 2, maxRetryCount: 5, updateTime: '2026-10-19T10:00:00', failReason: '网络暂时不可用，等待重新处理。' }] })
    if (path.endsWith('/mq/queues')) return json({ queues: empty ? [] : [{ queueName: 'order.stock.occupied.queue', messageCount: 3, backlogThreshold: 100, backlogAlarm: false }, { queueName: 'payment.refund.reconciliation.queue', messageCount: 120, backlogThreshold: 100, backlogAlarm: true }] })
    if (path.endsWith('/package-stock-adjustments')) return json({ packageId: body.packageId, operationId: body.operationId, stock: 20, sold: 8, txStatus: 'SUCCESS' })
    if (path.endsWith('/refund/confirm')) return json({ orderId: 101, orderStatus: 'REFUNDED', refundBehavior: 'REFUND_SUCCESS', refundOrderNo: 'REFUND101', paymentRefunded: true })
    if (path.endsWith('/payments/reconcile')) return json({ scannedPaymentCount: 10, successPaymentCount: 10, successRefundCount: 1, inconsistentPayOrderNos: [] })
    if (path.endsWith('/mq/messages/message-id/republish')) return json({ accepted: true })
    if (path === '/api/agent/runs') {
      run = { run_id: 'browserrun1', session_id: body.session_id, execution_status: 'waiting_input', response: '请核对套餐与金额，再确认下单。',
        trade: { thread_id: 'child1', session_id: body.session_id, status: 'awaiting_confirmation', action: 'create', response: '请确认', interrupt: { type: 'confirmation', confirmation_id: confirmationId, preview: { package_id: 42, quantity: 1, estimated_total_cents: 6000 } } },
        interrupt: { type: 'trade', task_id: 'trade_transaction', message: '请确认' } }
      return route.fulfill({ json: { run_id: run.run_id, session_id: run.session_id, request_id: body.request_id, execution_status: 'running' } })
    }
    if (path.endsWith('/resume')) {
      if (!disconnectConfirmation) {
        run = { ...run, execution_status: 'completed', response: '订单已创建，尚未付款。', interrupt: null,
          trade: { ...run.trade, status: 'completed', interrupt: null, result: { data: { orderId: 101, payAmount: 6000 } } } }
      }
      return route.fulfill({ json: { run_id: run.run_id, session_id: run.session_id, request_id: body.request_id, execution_status: 'running' } })
    }
    if (path.endsWith('/pause')) {
      run = { ...run, execution_status: 'paused' }
      pauseNextStream = false
      await pausedStream?.fulfill({ contentType: 'text/event-stream', body: 'event: turn_finished\ndata: {"status":"paused"}\n\n' })
      return route.fulfill({ json: run })
    }
    if (path.endsWith('/events')) {
      if (pauseNextStream) { pausedStream = route; return }
      const resumes = calls.filter(call => call.path.endsWith('/resume'))
      if (disconnectConfirmation && resumes.length) return route.abort('connectionreset')
      return route.fulfill({ contentType: 'text/event-stream', body: `id: 1\nevent: plan_ready\ndata: {}\n\nid: 2\nevent: turn_finished\ndata: {"status":"done"}\n\n` })
    }
    if (path === '/api/agent/runs/browserrun1') return route.fulfill({ json: run })
    throw new Error(`Unexpected API request: ${request.method()} ${path}`)
  })

  await page.goto(`${origin}/login`)
  for (const width of viewports) await layout('login', width)
  await capture('login-mobile')
  await page.setViewportSize({ width: 1440, height: 900 })
  await capture('login-desktop')
  await page.getByRole('textbox', { name: '手机号', exact: true }).fill('123')
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  check(!calls.some(call => call.path === '/user-api/login'), 'invalid login does not reach the API')
  await page.getByRole('textbox', { name: '手机号', exact: true }).fill('13900000000')
  await page.getByRole('textbox', { name: '验证码', exact: true }).press('Enter')
  await page.waitForURL(origin + '/', { timeout: 10_000 })
  checks.push('the login form accepts keyboard submission')
  await page.getByRole('heading', { name: '找好店，拼好价，把一顿饭安排明白。' }).waitFor()
  await page.locator('.shop-card').first().waitFor()
  check(new URL(page.url()).pathname === '/', 'login reaches the home page')
  await contrast('.agent-empty strong', 'assistant empty state')
  const firstShop = page.getByRole('link', { name: '测试店铺1', exact: true })
  await firstShop.focus()
  await page.keyboard.press('Tab'); await page.keyboard.press('Shift+Tab')
  check(await firstShop.evaluate(el => getComputedStyle(el).outlineStyle !== 'none'), 'shop cards have visible keyboard focus')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  check(await firstShop.evaluate(el => parseFloat(getComputedStyle(el).transitionDuration) < .01), 'reduced motion preference is respected')
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  pagedShops = true
  await page.getByRole('textbox', { name: '搜索店铺', exact: true }).fill('火锅')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.getByRole('button', { name: '加载更多店铺', exact: true }).waitFor()
  await page.getByRole('textbox', { name: '搜索店铺', exact: true }).fill('尚未提交的新搜索')
  await page.getByRole('button', { name: '加载更多店铺', exact: true }).click()
  await page.getByRole('link', { name: '测试店铺11', exact: true }).waitFor()
  check(await page.locator('.shop-card').count() === 11, 'home load more appends page two without replacing page one')
  check(calls.some(call => call.path === '/business-api/shop/of/name' && new URLSearchParams(call.search).get('name') === '火锅' && new URLSearchParams(call.search).get('current') === '2'), 'home page two preserves the applied search')
  check(await page.getByRole('button', { name: '加载更多店铺', exact: true }).count() === 0, 'a short last shop page hides load more')
  pagedShops = false
  await page.getByRole('link', { name: '测试店铺1', exact: true }).click()
  await page.getByRole('heading', { name: '测试店铺1', level: 1, exact: true }).waitFor()
  await page.evaluate(() => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/shops/2'))
  await page.getByRole('heading', { name: '测试店铺2', level: 1, exact: true }).waitFor()
  check(calls.filter(call => call.path.startsWith('/business-api/shop-homepage/')).length === 2, 'shop route reuse loads the new shop')

  await page.getByRole('textbox', { name: '交易助手消息' }).fill('帮我下单套餐42')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '确认操作', exact: true }).waitFor()
  await contrast('.agent-confirmation dd', 'transaction preview')
  await contrast('.agent-confirmation .el-button--primary', 'confirmation button')
  for (const width of viewports) await layout('agent confirmation', width)
  await capture('agent-confirmation-mobile')
  await page.setViewportSize({ width: 1440, height: 900 })
  await capture('agent-confirmation-desktop')
  await page.reload()
  await page.getByRole('button', { name: '确认操作', exact: true }).waitFor()
  check(calls.filter(call => call.path === '/api/agent/runs' && call.method === 'POST').length === 1, 'reload restores the original run without replanning')
  await page.getByRole('button', { name: '确认操作', exact: true }).click()
  await page.getByRole('button', { name: '重试上次请求', exact: true }).waitFor()
  check(calls.filter(call => call.path.endsWith('/resume')).length === 1, 'disconnected confirmation preserves a retry action')
  disconnectConfirmation = false
  await page.getByRole('button', { name: '重试上次请求', exact: true }).click()
  await page.getByText('订单已创建，尚未付款。', { exact: false }).waitFor()
  const resumes = calls.filter(call => call.path.endsWith('/resume'))
  check(resumes.length === 2 && resumes[0].body.request_id === resumes[1].body.request_id, 'confirmation retry preserves request_id')
  check(resumes.every(call => call.body.approved === true && call.body.confirmation_id === confirmationId && call.body.task_id === 'trade_transaction'), 'confirmation is bound to the original task and preview')
  check(await page.getByRole('button', { name: '重试上次请求', exact: true }).count() === 0, 'acknowledged completion clears the retry')

  pauseNextStream = true
  const waitingStream = page.waitForRequest(request => new URL(request.url()).pathname.endsWith('/events'))
  await page.getByRole('textbox', { name: '交易助手消息' }).fill('暂停恢复验收')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await waitingStream
  await page.getByRole('button', { name: '在检查点暂停', exact: true }).click()
  await page.getByRole('button', { name: '恢复协作任务', exact: true }).waitFor()
  const recoverCount = calls.filter(call => call.path.endsWith('/resume')).length
  await page.getByRole('button', { name: '恢复协作任务', exact: true }).click()
  await page.getByRole('button', { name: '恢复协作任务', exact: true }).waitFor({ state: 'hidden' })
  const recovered = calls.filter(call => call.path.endsWith('/resume')).slice(recoverCount)
  check(calls.some(call => call.path === '/api/agent/runs/browserrun1/pause'), 'the checkpoint pause control calls the current run')
  check(recovered.length === 1 && recovered[0].body.recover === true && recovered[0].body.task_id === 'trade_transaction', 'the paused-run control resumes the original task with recover=true')

  await page.getByRole('link', { name: '订单', exact: true }).click()
  await page.locator('.order-filters .el-select').first().click()
  const filteredOrders = page.waitForResponse(response => response.url().includes('tradeType=GROUP_BUY'))
  await page.getByRole('option', { name: '拼团', exact: true }).click()
  await filteredOrders
  check(calls.some(call => call.path === '/trade-api/orders' && call.search.includes('tradeType=GROUP_BUY')), 'order filter dropdown preserves the API query')
  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  await page.getByRole('button', { name: '再想想', exact: true }).click()
  check(!calls.some(call => call.path.endsWith('/cancel')), 'canceling the dialog sends no order write')
  await capture('orders-desktop')

  for (const [label, endpoint, quantity] of [['普通购买', 'normal', 2], ['拼团', 'group-buy', 2], ['秒杀', 'seckill', 1]]) {
    await page.goto(`${origin}/shops/1`)
    await page.getByRole('heading', { name: '测试店铺1', level: 1, exact: true }).waitFor()
    await page.getByRole('spinbutton', { name: '购买数量', exact: true }).fill(String(quantity))
    if (endpoint === 'group-buy') await page.getByRole('textbox', { name: '拼团队伍 ID', exact: true }).fill('TEAM42')
    const before = calls.filter(call => call.path === `/trade-api/orders/${endpoint}`).length
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.waitForURL(`${origin}/orders`)
    const writes = calls.filter(call => call.path === `/trade-api/orders/${endpoint}`).slice(before)
    check(writes.length === 1 && writes[0].body.quantity === quantity && (endpoint === 'seckill' ? writes[0].body.activityId === 3 : writes[0].body.packageId === 42), `${label} submits the selected purchase contract once`)
    if (endpoint === 'group-buy') check(writes[0].body.teamId === 'TEAM42', 'group purchase preserves the selected team')
    await page.getByRole('button', { name: '取消订单', exact: true }).click()
    await page.getByRole('button', { name: '确定取消', exact: true }).click()
    await page.locator('.order-card').getByText('已取消', { exact: true }).waitFor()
    check(order.orderStatus === 'CANCELED', `${label} cancellation confirmation updates the rendered order`)
  }
  check(calls.filter(call => call.path === '/trade-api/orders/101/cancel').length === 3, 'confirmed cancellations send one write per purchase')

  const routes = [
    ['/', '找好店，拼好价，把一顿饭安排明白。', 'home'], ['/shops/1', '测试店铺1', 'shop'], ['/orders', '我的订单', 'orders'],
    ['/operations/audit', '运营审计', 'audit'], ['/operations/orders', '订单检索', 'operation-orders'],
    ['/operations/stock', '库存调整', 'stock'], ['/operations/refunds', '退款确认', 'refunds'], ['/operations/messages', '异常消息处理', 'messages'],
  ]
  for (const [path, title, name] of routes) {
    await page.goto(`${origin}${path}`)
    await page.getByRole('heading', { name: title, level: 1, exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('.el-loading-mask'))
    const unresolved = await page.locator('main').evaluate(main => [...main.querySelectorAll('*')].filter(el => el.localName.startsWith('el-')).map(el => el.localName))
    check(unresolved.length === 0, `${name} resolves every registered UI component: ${unresolved.join(',')}`)
    for (const width of viewports) {
      await layout(name, width)
      if (width === 1440 || width === 390) await capture(`${name}-${width}`)
    }
    if (path.startsWith('/operations')) {
      check(await page.getByRole('navigation', { name: '运营功能' }).locator('a[aria-current="page"]').count() === 1, `${title} marks the active operations module`)
    }
  }

  // Exercise the actual image error handler, including a server-provided URL failure.
  await page.goto(origin)
  await page.locator('.shop-card__image').first().evaluate(img => { img.src = '/images/broken-fixture.jpg' })
  await page.waitForFunction(() => {
    const img = document.querySelector('.shop-card__image')
    return img?.src.endsWith('/images/meal-2.jpg') && img.complete && img.naturalWidth > 0
  })
  checks.push('a broken business image falls back to a local photograph')

  for (const [path, label] of [['/', '暂无店铺'], ['/orders', '暂无订单'], ['/operations/audit', '暂无审计记录'], ['/operations/orders', '暂无订单'], ['/operations/messages', '暂无消息'], ['/shops/1', '暂无在售套餐']]) {
    empty = true
    await page.goto(`${origin}${path}`)
    await page.getByText(label, { exact: true }).waitFor()
    await layout(`${path} empty state`, 320)
  }
  empty = false

  await page.goto(`${origin}/operations/stock`)
  await page.getByRole('spinbutton', { name: '套餐 ID', exact: true }).fill('42')
  await page.getByRole('button', { name: '提交调整', exact: true }).click()
  await page.getByRole('heading', { name: '调整成功', exact: true }).waitFor()
  check(calls.some(call => call.path.endsWith('/package-stock-adjustments') && call.body.packageId === 42), 'the restyled stock form submits its original API contract')
  await capture('stock-success-mobile')
  await page.goto(`${origin}/operations/refunds`)
  await page.getByRole('spinbutton', { name: '订单 ID', exact: true }).fill('101')
  await page.getByRole('button', { name: '确认退款', exact: true }).click()
  await page.getByRole('heading', { name: 'REFUND_SUCCESS', exact: true }).waitFor()
  await page.getByRole('button', { name: '支付对账', exact: true }).click()
  await page.getByText('支付 10', { exact: true }).waitFor()
  await layout('refund and reconciliation results', 320)
  await capture('refund-success-mobile')

  const completedRun = structuredClone(run)
  const child = completedRun.trade
  const scenarios = [
    ['selection', { trade: null, execution_status: 'waiting_input', interrupt: { type: 'selection', message: '请选择一个套餐', choices: [{ label: '双人火锅套餐', message: '选择套餐42' }, { label: '双人烤肉套餐', message: '选择套餐43' }] } }, '双人火锅套餐'],
    ['parameters', { execution_status: 'waiting_input', trade: { ...child, status: 'awaiting_parameters', interrupt: { type: 'parameters', message: '请补充购买数量', missing_fields: ['quantity'] } } }, null],
    ['awaiting-result', { execution_status: 'paused', trade: { ...child, status: 'awaiting_result', result: { data: { requestNo: 'seckill-request-'.repeat(8) } }, interrupt: { type: 'seckill_wait', message: '等待处理结果' } } }, '刷新秒杀请求结果'],
    ['uncertain', { execution_status: 'waiting_external', trade: { ...child, status: 'uncertain', result: {}, interrupt: null } }, '恢复并核对交易结果'],
    ['paused', { execution_status: 'paused', trade: null, interrupt: { type: 'paused', message: '协作任务已暂停' } }, '恢复协作任务'],
    ['purchase-options', { trade: { ...child, status: 'completed', action: 'advise', result: { data: { options: [{ trade_type: 'NORMAL', price_cents: 6000, message: '普通购买套餐42', conditions: '无需等待，可直接购买。', matches_preferences: true }] } } } }, '选择普通购买'],
    ['payment', { trade: { ...child, status: 'completed', action: 'pay', result: { data: { payOrderNo: 'PAY101', paymentForm: '<form method="post"></form>' } } } }, '打开支付宝沙箱收银台'],
  ]
  for (const [name, state, button] of scenarios) {
    run = { ...completedRun, ...state }
    await page.goto(origin)
    await page.locator('.agent-message').waitFor()
    if (button) await page.getByRole('button', { name: button, exact: true }).waitFor()
    if (name === 'parameters') check(await page.getByRole('textbox', { name: '交易助手消息' }).isEnabled(), 'parameter collection keeps the message field enabled')
    for (const width of [1440, 390, 320]) await layout(`agent ${name}`, width)
    await capture(`agent-${name}-mobile`)
  }
  run = completedRun
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(origin)
  await page.getByRole('heading', { name: '找好店，拼好价，把一顿饭安排明白。' }).waitFor()
  await capture('home-mobile')
  check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'mobile home has no horizontal overflow')
  expireOrders = true
  await page.getByRole('link', { name: '订单', exact: true }).click()
  await page.waitForURL('**/login')
  check(await page.evaluate(() => !localStorage.getItem('food-life-token')), 'HTTP 401 clears the rejected credential and redirects to login')
  check(expiredOrderRequests === 1, 'credential expiry does not remount a protected page and repeat its request')
  role = 'USER'; expireOrders = false
  await page.getByRole('textbox', { name: '手机号', exact: true }).fill('13900000000')
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(origin + '/')
  check(await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '运营', exact: true }).count() === 0, 'ordinary users do not see operations navigation')
  await page.goto(`${origin}/operations/stock`)
  await page.waitForURL(origin + '/')
  checks.push('ordinary users cannot enter an operations route')
  check(errors.length === 0, `no uncaught browser errors: ${errors.join('; ')}`)
  return { checks, count: checks.length, calls: calls.length, errors, screenshots, routes: 9, viewports, assistantStates: ['confirmation', 'retry', 'completed', ...scenarios.map(item => item[0])] }
}

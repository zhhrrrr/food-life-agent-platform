import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, defineStore, setActivePinia } from 'pinia'
import { loadSource, storage, reactive, nextTick } from './sourceHarness.mjs'

const notice = { error() {}, info() {}, success() {}, warning() {} }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const flush = async () => { await nextTick(); await new Promise(setImmediate) }

function dock(overrides = {}) {
  const auth = reactive({ user: { id: 7 }, isLogin: true })
  const cache = storage()
  const context = loadSource('components/AgentDock.vue', {
    useAuthStore: () => auth, ElMessage: notice, sessionStorage: cache,
    document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
    createSeckillPoller: () => ({ sync() {}, dispose() {} }),
    getAgentRun: async () => ({ run_id: 'run1', session_id: 's', execution_status: 'completed', response: 'ok' }),
    streamAgentRun: async () => {}, startAgentRun: async () => ({ run_id: 'run1', request_id: 'r' }),
    resumeAgentRun: async () => ({ run_id: 'run1', request_id: 'r' }),
    ...overrides,
  }, ['send', 'submit', 'pause', 'checkPayment', 'runId', 'agentRun', 'trade', 'retryBody', 'paymentStatus', 'busy', 'pausing'])
  return { ...context, auth, cache }
}

test('logout while the profile request is pending cannot restore the old user', async () => {
  const reply = deferred()
  setActivePinia(createPinia())
  const context = loadSource('stores/auth.ts', {
    defineStore, localStorage: storage(), sessionStorage: storage(), queryCurrentUser: () => reply.promise,
  }, ['useAuthStore'])
  try {
    const auth = context.useAuthStore(); auth.token = 'old'
    const pending = auth.fetchMe(); auth.logout()
    reply.resolve({ id: 7, role: 'ADMIN' }); await pending
    assert.equal(auth.user, null)
  } finally { context.dispose() }
})

test('a late profile failure cannot log out a newer login in AppLayout', async () => {
  let reject
  const auth = { token: 'old', fetchMe: () => new Promise((_, r) => { reject = r }), logout() { this.token = '' } }
  const context = loadSource('layouts/AppLayout.vue', { useAuthStore: () => auth, useRouter: () => ({}) }, [])
  try {
    context.hooks.mounted[0](); auth.token = 'new'; reject(new Error('offline')); await flush()
    assert.equal(auth.token, 'new')
  } finally { context.dispose() }
})

test('a delayed search response cannot overwrite the latest search', async () => {
  const first = deferred()
  const context = loadSource('views/HomeView.vue', {
    queryShopByName: async name => name === 'old' ? first.promise : [{ id: 2 }],
  }, ['keyword', 'loadShops', 'shops', 'loading'])
  try {
    context.keyword.value = 'old'; const pending = context.loadShops()
    context.keyword.value = 'new'; await context.loadShops()
    first.resolve([{ id: 1 }]); await pending
    assert.deepEqual(context.shops.value.map(s => s.id), [2])
    assert.equal(context.loading.value, false)
  } finally { context.dispose() }
})

test('a failed home search is handled without rejecting a UI event', async () => {
  const context = loadSource('views/HomeView.vue', {
    queryShopByName: async () => { throw new Error('offline') },
  }, ['keyword', 'loadShops', 'loading'])
  try {
    context.keyword.value = 'food'; await context.loadShops()
    assert.equal(context.loading.value, false)
  } finally { context.dispose() }
})

test('changing the shop route reloads the reused detail component', async () => {
  const route = reactive({ params: { id: '1' } })
  const calls = []
  const context = loadSource('views/ShopDetailView.vue', {
    useRoute: () => route, useRouter: () => ({}), useAuthStore: () => ({ user: { id: 7 } }), ElMessage: notice,
    queryShopHomepage: async id => { calls.push(id); return { shop: { id }, favorite: false } },
  }, ['homepage'])
  try {
    await Promise.all(context.hooks.mounted.map(fn => fn())); await flush()
    route.params.id = '2'; await flush()
    assert.equal(context.homepage.value.shop.id, 2)
    assert.deepEqual(calls, [1, 2])
  } finally { context.dispose() }
})

test('double clicking a package sends only one order creation', async () => {
  const reply = deferred(); let writes = 0
  const context = loadSource('views/ShopDetailView.vue', {
    useRoute: () => ({ params: { id: '1' } }), useRouter: () => ({ push() {} }),
    useAuthStore: () => ({ user: { id: 7 } }), ElMessage: notice,
    queryShopHomepage: async () => ({ shop: { id: 1 } }),
    createNormalOrder: () => { writes++; return reply.promise },
  }, ['orderNormal'])
  try {
    const a = context.orderNormal({ id: 42 }); const b = context.orderNormal({ id: 42 })
    reply.resolve({ orderNo: 'N1' }); await Promise.all([a, b])
    assert.equal(writes, 1)
  } finally { context.dispose() }
})

test('an invalid shop route stops the loading indicator of an older request', async () => {
  const reply = deferred(), route = reactive({ params: { id: '1' } })
  const context = loadSource('views/ShopDetailView.vue', {
    useRoute: () => route, useRouter: () => ({}), useAuthStore: () => ({ user: { id: 7 } }), ElMessage: notice,
    queryShopHomepage: () => reply.promise,
  }, ['loading', 'homepage'])
  try {
    route.params.id = 'invalid'; await flush()
    assert.equal(context.loading.value, false)
    reply.resolve({ shop: { id: 1 } }); await flush()
    assert.equal(context.homepage.value, null)
  } finally { reply.resolve({}); context.dispose() }
})

test('a late pause response cannot populate another account Agent state', async () => {
  const reply = deferred()
  const context = dock({ pauseAgentRun: () => reply.promise })
  try {
    context.runId.value = 'run1'; const pending = context.pause()
    context.auth.user = { id: 8 }; await nextTick()
    reply.resolve({ run_id: 'run1', execution_status: 'paused' }); await pending
    assert.equal(context.agentRun.value, null)
    assert.equal(context.pausing.value, false)
  } finally { context.dispose() }
})

test('a late payment-status response cannot populate another account Agent state', async () => {
  const reply = deferred()
  const context = dock({ queryPaymentStatus: () => reply.promise })
  try {
    context.trade.value = { thread_id: 't', action: 'pay', result: { data: { payOrderNo: 'P1' } } }
    const pending = context.checkPayment(); context.auth.user = { id: 8 }; await nextTick()
    reply.resolve({ payStatus: 'SUCCESS' }); await pending
    assert.equal(context.paymentStatus.value, '')
  } finally { context.dispose() }
})

test('a broken progress stream preserves the exact request while the run is still running', async () => {
  const context = dock({ streamAgentRun: async () => { throw new Error('offline') },
    getAgentRun: async () => ({ run_id: 'run1', session_id: 's', execution_status: 'running', response: 'working' }) })
  try {
    const body = { session_id: 's', request_id: 'r', message: 'create' }
    context.retryBody.value = body; await context.submit(body)
    assert.equal(context.retryBody.value?.request_id, 'r')
  } finally { context.dispose() }
})

test('a lost confirmation stream cannot discard the request based on an older waiting snapshot', async () => {
  const context = dock({ streamAgentRun: async () => { throw new Error('offline') },
    getAgentRun: async () => ({ run_id: 'run1', session_id: 's', execution_status: 'waiting_input', response: 'old preview' }) })
  try {
    const body = { session_id: 's', run_id: 'run1', request_id: 'r', message: 'confirm', approved: true, confirmation_id: 'C1' }
    await context.submit(body)
    assert.equal(context.retryBody.value?.confirmation_id, 'C1')
  } finally { context.dispose() }
})

test('a paused parent run cannot accept a hint as an ordinary resume reply', async () => {
  let calls = 0
  const context = dock({ resumeAgentRun: async () => { calls++; return { run_id: 'run1', request_id: 'r' } } })
  try {
    context.runId.value = 'run1'; context.agentRun.value = { execution_status: 'paused' }
    await context.send('看看我的订单'); assert.equal(calls, 0)
  } finally { context.dispose() }
})

test('a transient restore error preserves the saved Agent run and retry payload', async () => {
  const cache = storage()
  const saved = JSON.stringify({ sessionId: 's', runId: 'run1', retryBody: { session_id: 's', request_id: 'r', message: 'create' } })
  cache.setItem('food-agent:7', saved)
  const context = dock({ sessionStorage: cache, getAgentRun: async () => { throw new Error('offline') } })
  try { await flush(); assert.equal(cache.getItem('food-agent:7'), saved) }
  finally { context.dispose() }
})

test('restoring a snapshot does not silently consume an unacknowledged confirmation request', async () => {
  const cache = storage()
  cache.setItem('food-agent:7', JSON.stringify({ sessionId: 's', runId: 'run1', retryBody: {
    session_id: 's', run_id: 'run1', request_id: 'r', message: 'confirm', approved: true, confirmation_id: 'C1',
  } }))
  const context = dock({ sessionStorage: cache, getAgentRun: async () => ({
    run_id: 'run1', session_id: 's', execution_status: 'waiting_input', response: 'confirm preview',
  }) })
  try { await flush(); assert.equal(context.retryBody.value?.request_id, 'r') }
  finally { context.dispose() }
})

test('SSE parses CRLF split across network chunks and multiline JSON data', async () => {
  const chunks = ['id: 1\revent: ignored\r\n\r\n', 'id: 2\r', '\nevent: turn_finished\r', '\ndata: {\r', '\ndata: "status": "completed"}\r', '\n\r', '\n']
  let index = 0
  const response = new Response(new ReadableStream({ pull(controller) {
    if (index === chunks.length) controller.close()
    else controller.enqueue(new TextEncoder().encode(chunks[index++]))
  } }))
  const context = loadSource('api/agent.ts', { localStorage: storage(), fetch: async () => response }, ['streamAgentRun'])
  const events = []
  try {
    await context.streamAgentRun('run1', 'r', event => events.push(event))
    assert.deepEqual(events, [{ id: 2, event: 'turn_finished', data: { status: 'completed' } }])
    assert.equal(response.body.locked, false)
  } finally { context.dispose() }
})

test('SSE releases the reader as soon as the turn is finished', async () => {
  let reads = 0, cancelled = false, released = false
  const reader = { async read() {
    if (++reads > 1) throw new Error('The completed stream should not be read again')
    return { done: false, value: new TextEncoder().encode('event: turn_finished\ndata: {}\n\n') }
  }, async cancel() { cancelled = true }, releaseLock() { released = true } }
  const context = loadSource('api/agent.ts', { localStorage: storage(), fetch: async () => ({ ok: true, body: { getReader: () => reader } }) }, ['streamAgentRun'])
  try {
    await context.streamAgentRun('run1', 'r', () => {})
    assert.equal(cancelled, true); assert.equal(released, true)
  } finally { context.dispose() }
})

test('canceling a confirmation dialog does not reject the order UI handler', async () => {
  let writes = 0
  const context = loadSource('views/OrdersView.vue', {
    useAuthStore: () => ({ user: { id: 7 } }), useRoute: () => ({ query: {} }), sessionStorage: storage(), ElMessage: notice,
    ElMessageBox: { confirm: async () => { throw 'cancel' } }, cancelOrder: async () => { writes++ },
  }, ['handleCancel'])
  try { await context.handleCancel({ orderId: 1 }); assert.equal(writes, 0) }
  finally { context.dispose() }
})

test('an account switch during order confirmation cannot submit the old account order', async () => {
  const reply = deferred(); let writes = 0
  const auth = reactive({ user: { id: 7 } })
  const context = loadSource('views/OrdersView.vue', {
    useAuthStore: () => auth, useRoute: () => ({ query: {} }), sessionStorage: storage(), ElMessage: notice,
    ElMessageBox: { confirm: () => reply.promise }, cancelOrder: async () => { writes++ },
    queryOrders: async () => ({ orders: [], hasMore: false }),
  }, ['handleCancel'])
  try {
    const pending = context.handleCancel({ orderId: 1 }); auth.user = { id: 8 }; await nextTick()
    reply.resolve(); await pending; assert.equal(writes, 0)
  } finally { context.dispose() }
})

for (const [view, action, api, output, field] of [
  ['OperationAuditView', 'loadLogs', 'queryOperationAuditLogs', 'logs', 'logs'],
  ['OperationMessagesView', 'loadMessages', 'queryOperationMqMessages', 'messages', 'messages'],
  ['OperationOrdersView', 'loadOrders', 'queryOperationOrders', 'orders', 'orders'],
]) {
  test(`${view} ignores an older filtered response`, async () => {
    const reply = deferred(); let calls = 0
    const context = loadSource(`views/${view}.vue`, {
      useAuthStore: () => ({ user: { id: 7 } }), ElMessage: notice, trimOrUndefined: s => s?.trim() || undefined,
      [api]: async () => ++calls === 1 ? reply.promise : { [field]: [{ id: 2 }] },
    }, [action, output])
    try {
      const pending = context[action](); await context[action]()
      reply.resolve({ [field]: [{ id: 1 }] }); await pending
      assert.deepEqual(context[output].value.map(row => row.id), [2])
    } finally { context.dispose() }
  })
}

test('inventory adjustment rejects an empty quantity instead of sending an invalid write', async () => {
  let writes = 0
  const context = loadSource('views/OperationStockAdjustmentView.vue', {
    useAuthStore: () => ({ user: { id: 7 } }), ElMessage: notice,
    operationAdjustPackageStock: async () => { writes++; return {} },
  }, ['submit', 'form'])
  try {
    context.form.packageId = 42; context.form.adjustQuantity = undefined
    await context.submit(); assert.equal(writes, 0)
  } finally { context.dispose() }
})

test('operator navigation does not log out a newer credential after a late profile failure', async () => {
  let reject, guard
  const auth = { token: 'old', user: null, fetchMe: () => new Promise((_, r) => { reject = r }), logout() { this.token = '' } }
  const context = loadSource('router/index.ts', {
    AppLayout: {}, createWebHistory: () => ({}), createRouter: () => ({ beforeEach(fn) { guard = fn } }), useAuthStore: () => auth,
  }, [])
  try {
    const pending = guard({ name: 'operation-orders', meta: { requiresOperator: true } })
    auth.token = 'new'; reject(new Error('offline')); await pending
    assert.equal(auth.token, 'new')
  } finally { context.dispose() }
})

test('credential expiry redirects the mounted application to login', async () => {
  const auth = reactive({ token: 'old', user: { id: 7 } }); const paths = []
  const context = loadSource('layouts/AppLayout.vue', {
    useAuthStore: () => auth, useRouter: () => ({ replace: async path => { paths.push(path) } }),
  }, [])
  try { auth.token = ''; await nextTick(); assert.deepEqual(paths, ['/login']) }
  finally { context.dispose() }
})

test('a malformed phone cannot submit the login form', async () => {
  let writes = 0
  const context = loadSource('views/LoginView.vue', {
    useAuthStore: () => ({ loading: false, loginByCode: async () => { writes++ } }),
    useRoute: () => ({ query: {} }), useRouter: () => ({ push() {} }), ElMessage: notice,
  }, ['submit', 'form'])
  try {
    context.form.phone = '123'; context.form.code = '123456'; await context.submit()
    assert.equal(writes, 0)
  } finally { context.dispose() }
})

test('the login button cannot submit again while the previous login is running', async () => {
  let writes = 0
  const context = loadSource('views/LoginView.vue', {
    useAuthStore: () => ({ loading: true, loginByCode: async () => { writes++ } }),
    useRoute: () => ({ query: {} }), useRouter: () => ({ push() {} }), ElMessage: notice,
  }, ['submit', 'form'])
  try {
    context.form.phone = '13900000000'; context.form.code = '123456'; await context.submit()
    assert.equal(writes, 0)
  } finally { context.dispose() }
})

test('double clicking the operation refund button sends one refund', async () => {
  const reply = deferred(); let writes = 0
  const context = loadSource('views/OperationRefundView.vue', {
    ElMessage: notice, operationConfirmRefund: () => { writes++; return reply.promise },
  }, ['form', 'confirmRefund'])
  try {
    context.form.orderId = 1; const a = context.confirmRefund(); const b = context.confirmRefund()
    reply.resolve({ orderId: 1 }); await Promise.all([a, b]); assert.equal(writes, 1)
  } finally { context.dispose() }
})

test('leaving the inventory page ignores the pending adjustment response', async () => {
  const reply = deferred()
  const context = loadSource('views/OperationStockAdjustmentView.vue', {
    ElMessage: notice, operationAdjustPackageStock: () => reply.promise,
  }, ['submit', 'form', 'result'])
  try {
    context.form.packageId = 42; const pending = context.submit(); context.dispose()
    reply.resolve({ packageId: 42, stock: 100 }); await pending
    assert.equal(context.result.value, undefined)
  } finally { /* Already disposed while the request was pending. */ }
})

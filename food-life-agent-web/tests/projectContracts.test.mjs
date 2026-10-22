import test from 'node:test'
import assert from 'node:assert/strict'
import axios from 'axios'
import { loadSource, storage, reactive, nextTick } from './sourceHarness.mjs'

const notice = { error() {}, info() {}, success() {}, warning() {} }

test('401 clears the rejected credential, but a late 401 does not log out another account', async () => {
  const auth = { token: 'old-token', logout() { this.token = '' } }
  const client = loadSource('api/http.ts', { axios, ElMessage: notice, useAuthStore: () => auth }, ['http'])
  try {
    await assert.rejects(client.http.get('/test', { adapter: async config => { throw { config, response: { status: 401, data: {} } } } }))
    assert.equal(auth.token, '')
    auth.token = 'new-token'
    const delayed = client.http.interceptors.response.handlers[0].rejected
    await assert.rejects(delayed({ config: { headers: { authorization: 'old-token' } }, response: { status: 401 } }))
    assert.equal(auth.token, 'new-token')
  } finally { client.dispose() }
})

function dock(overrides = {}) {
  const auth = reactive({ user: { id: 7 }, isLogin: true })
  const responses = []
  const bindings = { useAuthStore: () => auth, ElMessage: notice, sessionStorage: storage(),
    document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} },
    createSeckillPoller: () => ({ sync() {}, dispose() {} }),
    startAgentRun: async () => ({ run_id: 'run1', request_id: 'request1' }),
    resumeAgentRun: async (...args) => { responses.push(args); return { run_id: 'run1', request_id: 'request1' } },
    streamAgentRun: async () => {}, getAgentRun: async () => ({ run_id: 'run1', session_id: 's', response: 'ok' }),
    getTradeWorkflow: async () => ({}), ...overrides }
  return { ...loadSource('components/AgentDock.vue', bindings, ['submit', 'resumePaused', 'runId', 'retryBody', 'agentRun']), auth, responses }
}

test('leaving AgentDock aborts the progress stream and ignores its late result', async () => {
  let finish, signal, reads = 0
  const context = dock({ streamAgentRun: async (_run, _request, _event, supplied) => {
    signal = supplied; await new Promise(resolve => { finish = resolve })
  }, getAgentRun: async () => { reads++; return { response: 'late' } } })
  const pending = context.submit({ message: 'hi', session_id: 's', request_id: 'r' })
  await nextTick(); context.dispose()
  try { assert.equal(signal?.aborted, true) }
  finally { finish(); await pending }
  assert.equal(reads, 0)
})

test('a failed paused-run recovery preserves recover=true when retried', async () => {
  let attempts = 0
  const calls = []
  const context = dock({ resumeAgentRun: async (...args) => {
    calls.push(args); if (++attempts === 1) throw new Error('offline')
    return { run_id: 'run1', request_id: 'r' }
  } })
  try {
    context.runId.value = 'run1'
    await context.resumePaused()
    assert.ok(context.retryBody.value)
    await context.submit(context.retryBody.value)
    assert.equal(calls[1][2], true)
  } finally { context.dispose() }
})

test('order list follows lastId and appends the next page', async () => {
  const calls = []
  const context = loadSource('views/OrdersView.vue', {
    useRoute: () => ({ query: {} }), useAuthStore: () => reactive({ user: { id: 7 } }),
    sessionStorage: storage(), ElMessage: notice,
    queryOrders: async request => { calls.push(request); return request.lastId
      ? { orders: [{ orderId: 10 }], hasMore: false, lastId: 10 }
      : { orders: [{ orderId: 20 }], hasMore: true, lastId: 20 } },
  }, ['loadOrders', 'orders'])
  try {
    await context.loadOrders(); await context.loadOrders(true)
    assert.equal(calls[1].lastId, 20)
    assert.deepEqual(context.orders.value.map(order => order.orderId), [20, 10])
  } finally { context.dispose() }
})

test('switching accounts ignores a late accepted Agent run', async () => {
  let accept, streams = 0
  const context = dock({ startAgentRun: () => new Promise(resolve => { accept = resolve }),
    streamAgentRun: async () => { streams++ } })
  const pending = context.submit({ message: 'hi', session_id: 's', request_id: 'r' })
  context.auth.user = { id: 8 }
  await nextTick()
  accept({ run_id: 'old-account-run', request_id: 'r' })
  await pending
  try { assert.equal(context.runId.value, ''); assert.equal(streams, 0) }
  finally { context.dispose() }
})

test('an older order request cannot overwrite a newer filtered result', async () => {
  let finish, calls = 0
  const context = loadSource('views/OrdersView.vue', {
    useRoute: () => ({ query: {} }), useAuthStore: () => ({ user: { id: 7 } }), sessionStorage: storage(), ElMessage: notice,
    queryOrders: async () => ++calls === 1 ? await new Promise(resolve => { finish = resolve })
      : { orders: [{ orderId: 2 }], hasMore: false },
  }, ['loadOrders', 'orders'])
  try {
    const stale = context.loadOrders()
    await context.loadOrders()
    finish({ orders: [{ orderId: 1 }], hasMore: false })
    await stale
    assert.deepEqual(context.orders.value.map(order => order.orderId), [2])
  } finally { context.dispose() }
})

test('pending payment belongs to its account and stops following the old account', async () => {
  const auth = reactive({ user: { id: 7 } })
  const cache = storage(); cache.setItem('food-life-pending-payment:7', 'PAY7'); cache.setItem('food-life-pending-payment:8', 'PAY8')
  const queries = []
  const context = loadSource('views/OrdersView.vue', {
    useRoute: () => ({ query: {} }), useAuthStore: () => auth, sessionStorage: cache, ElMessage: notice,
    queryPaymentStatus: async id => { queries.push(id); return { payStatus: 'PREPARED' } },
    queryOrders: async () => ({ orders: [], hasMore: false }),
  }, ['pendingPayment'])
  try {
    assert.equal(context.pendingPayment.value, 'PAY7')
    auth.user = { id: 8 }
    await nextTick(); await new Promise(setImmediate)
    assert.equal(context.pendingPayment.value, 'PAY8')
    assert.deepEqual(queries, ['PAY8'])
    assert.equal(cache.getItem('food-life-pending-payment:7'), 'PAY7')
  } finally { context.dispose() }
})

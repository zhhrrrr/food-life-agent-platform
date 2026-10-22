<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChatDotRound, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getAgentRun, getTradeWorkflow, pauseAgentRun, recoverTradeWorkflow, resumeAgentRun,
  startAgentRun, streamAgentRun, type AgentRequest, type AgentResponse, type TradeWorkflow,
} from '../api/agent'
import { queryPaymentStatus } from '../api/trade'
import { useAuthStore } from '../stores/auth'
import { createAlipayPaymentForm } from '../utils/alipayPayment'
import { createSeckillPoller, type PollingPhase } from '../utils/seckillPolling'
import type { ShopHomepage } from '../types/shop'

const props = defineProps<{
  homepage?: ShopHomepage | null
}>()

const auth = useAuthStore()
const input = ref('')
const busy = ref(false)
const sessionId = ref(crypto.randomUUID().replaceAll('-', ''))
const trade = ref<TradeWorkflow | null>(null)
const agentRun = ref<AgentResponse | null>(null)
const runId = ref('')
const messages = ref<{ role: string; text: string }[]>([])
const retryBody = ref<AgentRequest | null>(null)
const paymentStatus = ref('')
const progress = ref('')
const pausing = ref(false)
const pollingPhase = ref<PollingPhase>('idle')
const pageVisible = ref(document.visibilityState === 'visible')
let disposed = false
let contextVersion = 0
let streamController: AbortController | undefined
function stopStream() {
  streamController?.abort()
  streamController = undefined
}
const paymentForm = computed(() => trade.value?.action === 'pay' && trade.value.status === 'completed' ? trade.value.result?.data?.paymentForm : null)
const paymentNo = computed(() => trade.value?.action === 'pay' ? trade.value.result?.data?.payOrderNo : null)
const awaitingConfirmation = computed(() => trade.value?.status === 'awaiting_confirmation')
const awaitingResult = computed(() => trade.value?.status === 'awaiting_result')
const purchaseOptions = computed(() => {
  if (agentRun.value?.purchase_options?.length) return agentRun.value.purchase_options
  const options = trade.value?.action === 'advise' ? trade.value.result?.data?.options : null
  return Array.isArray(options) ? options.filter((item): item is {trade_type: string; price_cents: number; message: string; conditions: string; matches_preferences: boolean} =>
    !!item && typeof item.message === 'string' && typeof item.price_cents === 'number') : []
})
const typeLabels: Record<string, string> = { NORMAL: '普通购买', GROUP_BUY: '拼团购买', SECKILL: '秒杀购买' }
const recoverable = computed(() => ['uncertain', 'unauthorized', 'running'].includes(trade.value?.status || ''))
const awaitingSelection = computed(() => agentRun.value?.interrupt?.type === 'selection')
const selectionChoices = computed(() => agentRun.value?.interrupt?.choices || [])
const parentWaiting = computed(() => ['waiting_input', 'waiting_external', 'paused', 'pausing', 'interrupted'].includes(agentRun.value?.execution_status || ''))
const parentPaused = computed(() => ['paused', 'interrupted'].includes(agentRun.value?.execution_status || ''))
const needsParentSync = computed(() => agentRun.value?.interrupt?.type === 'trade'
  && ['completed', 'failed', 'rejected', 'expired', 'stale', 'clarification'].includes(trade.value?.status || ''))
const previewLabels: Record<string, string> = {
  package_id: '套餐编号', package_name: '套餐名称', quantity: '数量', unit_price_cents: '单价',
  estimated_total_cents: '优惠前估算', user_coupon_id: '优惠券编号', amount_note: '金额说明',
  order_id: '订单编号', order_status: '订单状态', pay_amount_cents: '订单金额', refund_reason: '退款原因',
  trade_type: '购买方式', activity_id: '活动编号', group_mode: '开团或参团', team_id: '团队编号',
  target_count: '成团人数', remaining_count: '剩余名额', valid_end_time: '截止时间', group_note: '成团说明',
  stock_note: '库存说明', user_take_limit: '限购数量', pay_order_no: '支付单号', pay_status: '支付状态',
}
const actionLabels: Record<string, string> = { create: '创建订单', pay: '支付宝沙箱支付', cancel: '取消订单', refund: '申请退款', sync_payment: '核对支付宝支付结果' }
const previewRows = computed(() => Object.entries(trade.value?.interrupt?.preview || {})
  .filter(([, value]) => value !== null && value !== '')
  .map(([key, value]) => ({ label: previewLabels[key] || key, value: key.endsWith('_cents') && typeof value === 'number' ? `¥${(value / 100).toFixed(2)}` : key === 'trade_type' ? typeLabels[String(value)] || String(value) : key === 'group_mode' ? value === 'open' ? '开团' : '参团' : String(value) })))

function persist() {
  if (auth.user) sessionStorage.setItem(`food-agent:${auth.user.id}`, JSON.stringify({
    sessionId: sessionId.value, runId: runId.value, threadId: trade.value?.thread_id, retryBody: retryBody.value,
  }))
}

function applyResult(result: AgentResponse, recordMessage = true, preserveRetry = false) {
  sessionId.value = result.session_id
  runId.value = result.run_id
  agentRun.value = result
  trade.value = result.trade || null
  paymentStatus.value = ''
  if (recordMessage) messages.value.push({ role: 'assistant', text: result.response })
  if (!preserveRetry && !['running', 'pausing'].includes(result.execution_status)) retryBody.value = null
  persist()
}

watch(() => auth.user?.id, async (userId) => {
  const version = ++contextVersion
  const isCurrent = () => !disposed && contextVersion === version && auth.user?.id === userId
  stopStream()
  busy.value = false
  pausing.value = false
  input.value = ''
  paymentStatus.value = ''
  progress.value = ''
  trade.value = null
  agentRun.value = null
  runId.value = ''
  messages.value = []
  retryBody.value = null
  sessionId.value = crypto.randomUUID().replaceAll('-', '')
  if (!userId) return
  const saved = sessionStorage.getItem(`food-agent:${userId}`)
  if (!saved) return
  busy.value = true
  try {
    const context = JSON.parse(saved)
    if (!context || typeof context.sessionId !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(context.sessionId)) {
      throw new SyntaxError('Invalid saved Agent session')
    }
    sessionId.value = context.sessionId
    runId.value = context.runId || ''
    retryBody.value = context.retryBody || null
    if (context.runId) {
      const restored = await getAgentRun(context.runId)
      if (!isCurrent()) return
      // Reading a snapshot does not acknowledge the saved request. Retry it
      // with the same request_id so the server can replay its receipt safely.
      applyResult(restored, true, true)
    } else if (context.threadId) {
      const restored = await getTradeWorkflow(context.threadId)
      if (!isCurrent()) return
      trade.value = restored
      messages.value.push({ role: 'assistant', text: restored.response })
    }
  } catch (error) {
    if (!isCurrent()) return
    if (error instanceof SyntaxError || (error as { response?: { status?: number } }).response?.status === 404) {
      sessionStorage.removeItem(`food-agent:${userId}`)
      runId.value = ''
      retryBody.value = null
    } else if (runId.value) {
      agentRun.value = { session_id: sessionId.value, run_id: runId.value, execution_status: 'interrupted', response: '暂时无法读取运行，请恢复原运行。' }
    }
  } finally {
    if (isCurrent()) busy.value = false
  }
}, { immediate: true })

async function send(message = input.value, decision?: boolean) {
  if (busy.value || !message.trim()) return
  if (!auth.isLogin) { ElMessage.info('请先登录后使用交易助手'); return }
  if (!auth.user) { ElMessage.info('正在加载登录信息，请稍后重试'); return }
  if (parentPaused.value) { ElMessage.info('请先恢复协作任务'); return }
  if (recoverable.value || retryBody.value) { ElMessage.info('请先恢复交易或重试上次请求'); return }
  if (awaitingResult.value) { ElMessage.info('秒杀请求已提交，请刷新结果，不要重复购买'); return }
  if (awaitingConfirmation.value && decision === undefined) { ElMessage.info('请先核对预览并点击确认或放弃'); return }
  const body: AgentRequest = { message, session_id: sessionId.value, request_id: crypto.randomUUID() }
  if (parentWaiting.value && runId.value) {
    body.run_id = runId.value
    body.task_id = agentRun.value?.interrupt?.task_id
  }
  if (decision !== undefined) {
    body.confirmation_id = trade.value?.interrupt?.confirmation_id
    body.approved = decision
  }
  retryBody.value = body
  persist()
  messages.value.push({ role: 'user', text: message })
  input.value = ''
  await submit(body)
}

async function submit(body: AgentRequest) {
  if (busy.value || disposed) return
  busy.value = true
  retryBody.value = body
  persist()
  const userId = auth.user?.id
  stopStream()
  const controller = new AbortController()
  streamController = controller
  const isCurrent = () => !disposed && auth.user?.id === userId && streamController === controller
  try {
    const accepted = body.run_id ? await resumeAgentRun(body.run_id, body, Boolean(body.recover)) : await startAgentRun(body)
    if (!isCurrent()) return
    runId.value = accepted.run_id
    persist()
    let streamSettled = false
    try {
      await streamAgentRun(accepted.run_id, accepted.request_id, (event) => {
        if (!isCurrent()) return
        if (event.event === 'turn_finished' || event.event === 'error') streamSettled = true
        if (event.event === 'error') ElMessage.error(String(event.data.message || '请求暂未完成，请恢复原运行'))
        else if (event.event === 'plan_ready') progress.value = '已生成执行计划'
        else if (event.event === 'task_started') progress.value = `正在执行 ${String(event.data.agent || '')} 子任务`
        else if (event.event === 'trade_state') progress.value = `交易流程：${String(event.data.status || '')}`
        else if (event.event === 'pause_requested') progress.value = '正在安全暂停'
      }, controller.signal)
    } catch { if (isCurrent()) progress.value = '进度连接已断开，正在读取持久化状态' }
    if (!isCurrent()) return
    const result = await getAgentRun(accepted.run_id)
    if (!isCurrent()) return
    progress.value = ''
    applyResult(result, true, !streamSettled)
  } catch { /* HTTP interceptor displays errors; preserve the exact request for retry. */ }
  finally { if (streamController === controller) { streamController = undefined; busy.value = false } }
}

async function recover(automatic = false) {
  if (busy.value || !trade.value) return
  if (automatic && (!awaitingResult.value || !pageVisible.value || parentPaused.value || !auth.isLogin)) return
  busy.value = true
  const userId = auth.user?.id
  const threadId = trade.value.thread_id
  const currentRunId = runId.value
  stopStream()
  const controller = new AbortController()
  streamController = controller
  const isCurrent = () => !disposed && auth.user?.id === userId && trade.value?.thread_id === threadId && runId.value === currentRunId && streamController === controller
  try {
    if (runId.value) {
      const body: AgentRequest = { message: '', session_id: sessionId.value, request_id: crypto.randomUUID(), run_id: runId.value,
        task_id: agentRun.value?.interrupt?.task_id }
      const accepted = await resumeAgentRun(runId.value, body, true)
      if (!isCurrent()) return
      try {
        await streamAgentRun(accepted.run_id, accepted.request_id, () => undefined, controller.signal)
      } catch { /* A lost progress stream is reconciled from the persisted run. */ }
      if (!isCurrent()) return
      const restored = await getAgentRun(accepted.run_id)
      if (!isCurrent()) return
      applyResult(restored, !automatic || restored.trade?.status !== 'awaiting_result')
      return
    }
    const restored = await recoverTradeWorkflow(trade.value, crypto.randomUUID())
    if (!isCurrent()) return
    trade.value = restored
    if (!automatic || restored.status !== 'awaiting_result') messages.value.push({ role: 'assistant', text: restored.response })
    persist()
  } catch (error) {
    // The polling controller backs off; manual errors are displayed by HTTP.
    if (automatic) throw error
  }
  finally { if (streamController === controller) { streamController = undefined; busy.value = false } }
}

const seckillPoller = createSeckillPoller({
  snapshot: () => ({
    key: auth.user && trade.value ? `${auth.user.id}:${trade.value.thread_id}` : null,
    waiting: awaitingResult.value && auth.isLogin,
    ready: !busy.value && pageVisible.value && !parentPaused.value && !retryBody.value,
  }),
  refresh: () => recover(true),
  onPhase: (phase) => { pollingPhase.value = phase },
})

watch([awaitingResult, () => trade.value?.thread_id, () => auth.user?.id,
  () => auth.isLogin, busy, pageVisible, parentPaused, retryBody], () => seckillPoller.sync(), { immediate: true })

function updateVisibility() {
  pageVisible.value = document.visibilityState === 'visible'
}

onMounted(() => document.addEventListener('visibilitychange', updateVisibility))
onBeforeUnmount(() => {
  disposed = true
  stopStream()
  seckillPoller.dispose()
  document.removeEventListener('visibilitychange', updateVisibility)
})

async function resumePaused() {
  if (!runId.value || busy.value) return
  const body: AgentRequest = { message: '', session_id: sessionId.value, request_id: crypto.randomUUID(), run_id: runId.value,
    task_id: agentRun.value?.interrupt?.task_id, recover: true }
  retryBody.value = body
  persist()
  await submit(body)
}

async function pause() {
  if (!runId.value || pausing.value) return
  pausing.value = true
  const version = contextVersion
  const id = runId.value
  const previous = agentRun.value
  try {
    const result = await pauseAgentRun(id)
    if (disposed || version !== contextVersion || runId.value !== id || agentRun.value !== previous) return
    agentRun.value = result
    progress.value = '已请求在当前任务完成后的检查点暂停'
  } catch { /* HTTP interceptor displays errors. */ }
  finally { if (version === contextVersion) pausing.value = false }
}

function openCheckout() {
  if (typeof paymentForm.value !== 'string') return
  try {
    const form = createAlipayPaymentForm(paymentForm.value)
    const name = `food-agent-checkout-${Date.now()}`
    const checkout = window.open('', name)
    if (!checkout) { ElMessage.info('请允许打开支付宝收银台窗口'); return }
    form.target = name
    document.body.appendChild(form)
    HTMLFormElement.prototype.submit.call(form)
    form.remove()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '支付表单无效') }
}

async function checkPayment() {
  if (busy.value || typeof paymentNo.value !== 'string') return
  busy.value = true
  const version = contextVersion
  const number = paymentNo.value
  try {
    const result = await queryPaymentStatus(number)
    if (!disposed && version === contextVersion && paymentNo.value === number) paymentStatus.value = result.payStatus
  }
  catch { /* The HTTP interceptor displays the error. */ }
  finally { if (version === contextVersion) busy.value = false }
}

const hints = computed(() => {
  if (!props.homepage) {
    return ['今晚想吃什么', '找高性价比套餐', '看看我的订单']
  }
  const shop = props.homepage.shop
  const pack = props.homepage.packages[0]
  return [
    `${shop.name} 适合几人吃`,
    pack ? `${pack.name} 怎么买划算` : '附近还有哪些套餐',
    props.homepage.favorite ? '查看我的收藏店铺' : '收藏这家店',
  ]
})
</script>

<template>
  <aside class="agent-dock">
    <div class="agent-dock__head">
      <el-icon><ChatDotRound /></el-icon>
      <span>美食拼团 Agent</span>
      <span class="agent-dock__status" role="status">{{ busy ? '处理中' : '随时为你服务' }}</span>
    </div>
    <p>帮你挑套餐、比价格、跟进订单状态。</p>
    <div class="agent-dock__chips">
      <button v-for="hint in hints" :key="hint" type="button" :disabled="busy" @click="send(hint)">
        <el-icon><MagicStick /></el-icon>
        {{ hint }}
      </button>
    </div>
    <div v-if="messages.length === 0 && !busy" class="agent-empty">
      <el-icon><ChatDotRound /></el-icon>
      <strong>把这一餐，交给我帮你安排</strong>
      <p>挑套餐、看规则，确认后再下单。</p>
    </div>
    <div class="agent-dialog" aria-live="polite">
      <div v-for="(entry, index) in messages" :key="index" class="agent-message" :class="{ 'agent-message--user': entry.role === 'user' }"><strong>{{ entry.role === 'user' ? '你' : '助手' }}</strong>{{ entry.text }}</div>
      <p v-if="progress" class="agent-progress"><strong>进度：</strong>{{ progress }}</p>
    </div>
    <div v-if="awaitingSelection" class="agent-confirmation">
      <p><strong>{{ agentRun?.interrupt?.message }}</strong></p>
      <div class="agent-actions"><el-button v-for="choice in selectionChoices" :key="choice.message" :disabled="busy" @click="send(choice.message)">{{ choice.label }}</el-button></div>
    </div>
    <div v-if="awaitingConfirmation" class="agent-confirmation">
      <p><strong>本次操作：{{ actionLabels[trade?.action || ''] }}</strong></p>
      <dl class="agent-preview"><template v-for="row in previewRows" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></template></dl>
      <div class="agent-actions">
        <el-button type="primary" :disabled="busy" @click="send('确认本次操作', true)">确认操作</el-button>
        <el-button :disabled="busy" @click="send('放弃本次操作', false)">放弃</el-button>
      </div>
    </div>
    <div v-if="trade?.result?.data" class="agent-result">
      <p v-if="trade.action === 'create' && trade.status === 'completed'">订单编号：{{ trade.result.data.orderId }}<template v-if="typeof trade.result.data.payAmount === 'number'">，应付金额：¥{{ (trade.result.data.payAmount / 100).toFixed(2) }}</template></p>
      <el-button v-if="trade.action === 'create' && trade.status === 'completed'" :disabled="busy" @click="send(`支付订单${trade.result.data.orderId}`)">支付宝沙箱支付</el-button>
      <el-button v-if="paymentForm" :disabled="busy" @click="openCheckout">打开支付宝沙箱收银台</el-button>
      <el-button v-if="paymentNo" :disabled="busy" @click="checkPayment">查询支付结果</el-button>
      <el-button v-if="paymentNo" :disabled="busy" @click="send(`同步支付单${paymentNo}`)">向支付宝核对结果</el-button>
      <p v-if="paymentStatus">支付状态：{{ paymentStatus }}</p>
      <p v-if="awaitingResult">秒杀请求单号：{{ trade.result.data.requestNo }}，尚未生成订单。</p>
      <p v-if="awaitingResult && pollingPhase === 'polling'">正在自动查询秒杀结果，请稍候。</p>
      <p v-if="awaitingResult && pollingPhase === 'paused'">自动查询已暂停，返回页面或恢复协作任务后继续。</p>
      <p v-if="awaitingResult && pollingPhase === 'timed_out'">暂未取得最终结果，自动查询已结束。可手动刷新，无需重复购买。</p>
      <el-button v-if="awaitingResult" :disabled="busy" @click="recover()">刷新秒杀请求结果</el-button>
      <div v-for="option in purchaseOptions" :key="option.message" class="agent-option">
        <p>{{ typeLabels[option.trade_type] }}：¥{{ (option.price_cents / 100).toFixed(2) }}。{{ option.conditions }}<span v-if="!option.matches_preferences">（不满足当前预算或等待偏好）</span></p>
        <el-button :disabled="busy" @click="send(option.message)">选择{{ typeLabels[option.trade_type] }}</el-button>
      </div>
      <details v-if="trade.action === 'query'"><summary>查询结果</summary><pre>{{ JSON.stringify(trade.result.data, null, 2) }}</pre></details>
    </div>
    <div v-if="recoverable || needsParentSync || parentPaused || (busy && runId) || retryBody" class="agent-actions">
      <el-button v-if="recoverable" :disabled="busy" @click="recover()">恢复并核对交易结果</el-button>
      <el-button v-if="needsParentSync" :disabled="busy" @click="recover()">同步交易结果到协作任务</el-button>
      <el-button v-if="parentPaused" :disabled="busy" @click="resumePaused">恢复协作任务</el-button>
      <el-button v-if="busy && runId" :loading="pausing" @click="pause">在检查点暂停</el-button>
      <el-button v-if="retryBody" :disabled="busy" @click="submit(retryBody)">重试上次请求</el-button>
    </div>
    <form class="agent-input" @submit.prevent="send()">
      <el-input v-model="input" :disabled="busy || awaitingConfirmation || awaitingSelection || awaitingResult || recoverable || parentPaused || !!retryBody" maxlength="4000" placeholder="例如：套餐1怎么买划算；开团套餐1；秒杀活动1" aria-label="交易助手消息" />
      <el-button native-type="submit" type="primary" :disabled="!input.trim() || awaitingConfirmation || awaitingSelection || awaitingResult || recoverable || parentPaused || !!retryBody" :loading="busy">发送</el-button>
    </form>
  </aside>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderCard from '../components/OrderCard.vue'
import { applyRefund, cancelOrder, preparePayment, queryOrders, queryPaymentStatus, syncPayment, useOrder } from '../api/trade'
import type { OrderInfo, OrderStatus, TradeType } from '../types/order'
import { createAlipayPaymentForm } from '../utils/alipayPayment'
import { useAuthStore } from '../stores/auth'
const route = useRoute()
const auth = useAuthStore()
const paymentKey = () => `food-life-pending-payment:${auth.user?.id}`
const pendingPayment = ref(auth.user ? sessionStorage.getItem(paymentKey()) || '' : '')
let pollTimer: ReturnType<typeof setTimeout> | undefined
let active = true
let pollDeadline = Date.now() + 30 * 60 * 1000
let checkoutWindow: Window | null = null

const orders = ref<OrderInfo[]>([])
const loading = ref(false)
const hasMore = ref(false)
const lastId = ref<number>()
let listVersion = 0
const busyOrderId = ref<number>()
const filters = reactive<{
  tradeType: TradeType | ''
  orderStatus: OrderStatus | ''
}>({
  tradeType: '',
  orderStatus: '',
})

async function loadOrders(append = false) {
  const loadMore = append === true
  if (loadMore && (loading.value || !hasMore.value)) return
  const version = ++listVersion
  const owner = auth.user?.id
  loading.value = true
  try {
    const result = await queryOrders({
      tradeType: filters.tradeType,
      orderStatus: filters.orderStatus,
      pageSize: 20,
      lastId: loadMore ? lastId.value : undefined,
    })
    if (!active || owner !== auth.user?.id || version !== listVersion) return
    orders.value = loadMore ? [...orders.value, ...result.orders] : result.orders
    hasMore.value = result.hasMore
    lastId.value = result.lastId
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === listVersion) loading.value = false
  }
}

async function runOrderAction(order: OrderInfo, confirm: () => Promise<unknown>, action: () => Promise<string>) {
  if (busyOrderId.value !== undefined) return
  const owner = auth.user?.id
  busyOrderId.value = order.orderId
  try {
    await confirm()
    if (!active || owner !== auth.user?.id) return
    const message = await action()
    if (!active || owner !== auth.user?.id) return
    ElMessage.success(message)
    await loadOrders()
  } catch {
    // Dialog cancellation is normal; API failures are reported by HTTP.
  } finally {
    if (owner === auth.user?.id) busyOrderId.value = undefined
  }
}

async function handlePay(order: OrderInfo) {
  if (busyOrderId.value !== undefined) return
  if (checkoutWindow && !checkoutWindow.closed) {
    checkoutWindow.focus()
    return
  }
  // Open before awaiting the API so browsers treat this as a user-initiated popup.
  const checkoutName = `food-life-checkout-${order.orderId}-${Date.now()}`
  const checkout = window.open('', checkoutName)
  if (!checkout) {
    ElMessage.warning('请允许打开支付窗口，然后重新点击立即支付')
    return
  }
  busyOrderId.value = order.orderId
  const owner = auth.user?.id
  let receivedCheckout = false
  try {
    const paymentOrder = await preparePayment(order.orderId)
    if (!active || owner !== auth.user?.id) { checkout.close(); return }
    receivedCheckout = true
    if (!paymentOrder.paymentForm) throw new Error('未获取到支付宝支付表单，请刷新页面后重试')
    const form = createAlipayPaymentForm(paymentOrder.paymentForm)
    form.target = checkoutName
    rememberPayment(paymentOrder.payOrderNo)
    checkoutWindow = checkout
    pollDeadline = Date.now() + 30 * 60 * 1000
    document.body.appendChild(form)
    HTMLFormElement.prototype.submit.call(form)
    form.remove()
    schedulePaymentPoll()
  } catch (error) {
    checkout.close()
    // Request failures are already shown by the API interceptor.
    if (receivedCheckout) ElMessage.error(error instanceof Error ? error.message : '发起支付宝支付失败')
  } finally {
    if (owner === auth.user?.id) busyOrderId.value = undefined
  }
}

async function handleCancel(order: OrderInfo) {
  await runOrderAction(order, () => ElMessageBox.confirm('取消后会释放已占用库存，确定取消这笔订单吗？', '取消订单', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning',
  }), async () => {
    await cancelOrder(order.orderId)
    return '订单已取消'
  })
}

async function handleRefund(order: OrderInfo) {
  await runOrderAction(order, () => ElMessageBox.confirm('退款会回退支付状态并触发库存补偿，确定继续吗？', '申请退款', {
    confirmButtonText: '确认退款',
    cancelButtonText: '暂不退款',
    type: 'warning',
  }), async () => {
    const result = await applyRefund(order.orderId)
    return result.paymentRefunded ? '支付宝退款完成' : '退款申请已处理'
  })
}

async function handleUse(order: OrderInfo) {
  await runOrderAction(order, () => ElMessageBox.confirm('请确认用户已到店消费，核销后订单不可再次退款。', '到店核销', {
    confirmButtonText: '确认核销',
    cancelButtonText: '先不核销',
    type: 'info',
  }), async () => {
    await useOrder(order.orderId)
    return '核销成功'
  })
}

async function refreshOrders(reconcile = false) {
  const owner = auth.user?.id
  try {
    if (pendingPayment.value) {
      const payOrderNo = pendingPayment.value
      const result = await (reconcile ? syncPayment(payOrderNo) : queryPaymentStatus(payOrderNo))
      if (!active || owner !== auth.user?.id || pendingPayment.value !== payOrderNo) return
      if (['SUCCESS', 'REFUNDED', 'CLOSED'].includes(result.payStatus)) {
        rememberPayment('')
        if (pollTimer) clearTimeout(pollTimer)
        checkoutWindow?.close()
        checkoutWindow = null
        if (result.payStatus === 'SUCCESS') ElMessage.success('支付宝支付已确认')
        if (result.payStatus === 'REFUNDED') ElMessage.info('该笔支付已原路退款')
      }
    }
  } catch {
    // Keep the payment identifier for a later reconciliation attempt.
  } finally {
    if (active && owner === auth.user?.id) await loadOrders()
  }
}

function rememberPayment(payOrderNo: string) {
  pendingPayment.value = payOrderNo
  if (!auth.user) return
  if (payOrderNo) sessionStorage.setItem(paymentKey(), payOrderNo)
  else sessionStorage.removeItem(paymentKey())
}

watch(() => auth.user?.id, async (userId) => {
  if (pollTimer) clearTimeout(pollTimer)
  checkoutWindow?.close()
  checkoutWindow = null
  ++listVersion
  loading.value = false
  busyOrderId.value = undefined
  orders.value = []
  hasMore.value = false
  lastId.value = undefined
  pendingPayment.value = userId ? sessionStorage.getItem(paymentKey()) || '' : ''
  pollDeadline = Date.now() + 30 * 60 * 1000
  if (!userId || !active) return
  await refreshOrders()
  schedulePaymentPoll()
})

async function pollPayment() {
  if (!active || !pendingPayment.value || Date.now() >= pollDeadline) return
  await refreshOrders()
  schedulePaymentPoll()
}

function schedulePaymentPoll() {
  if (pollTimer) clearTimeout(pollTimer)
  if (active && pendingPayment.value && Date.now() < pollDeadline) {
    pollTimer = setTimeout(pollPayment, 5000)
  }
}

onMounted(async () => {
  if (typeof route.query.payOrderNo === 'string') rememberPayment(route.query.payOrderNo)
  await refreshOrders()
  schedulePaymentPoll()
})
onUnmounted(() => { active = false; if (pollTimer) clearTimeout(pollTimer) })
</script>

<template>
  <section class="orders-view">
    <div class="section-head">
      <div>
        <p class="eyebrow">每一餐，都有记录</p>
        <h1>我的订单</h1>
        <p class="page-description">付款、到店、退款，在这里跟进每一步。</p>
      </div>
      <div class="order-filters">
        <el-button :loading="loading" @click="refreshOrders(true)">刷新订单</el-button>
        <el-select v-model="filters.tradeType" aria-label="筛选业务类型" placeholder="业务类型" clearable @change="loadOrders()">
          <el-option label="普通购买" value="NORMAL" />
          <el-option label="拼团" value="GROUP_BUY" />
          <el-option label="秒杀" value="SECKILL" />
        </el-select>
        <el-select v-model="filters.orderStatus" aria-label="筛选订单状态" placeholder="订单状态" clearable @change="loadOrders()">
          <el-option label="待付款" value="WAIT_PAY" />
          <el-option label="已付款" value="PAID" />
          <el-option label="已核销" value="USED" />
          <el-option label="已取消" value="CANCELED" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
      </div>
    </div>

    <el-alert
      v-if="pendingPayment"
      title="请在支付宝收银台扫码付款，付款后本页会自动更新。"
      type="info"
      :closable="false"
      show-icon
    />

    <div v-loading="loading" class="order-list">
      <OrderCard
        v-for="order in orders"
        :key="order.orderId"
        :order="order"
        :busy="busyOrderId === order.orderId"
        @pay="handlePay"
        @cancel="handleCancel"
        @refund="handleRefund"
        @use="handleUse"
      />
      <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />
      <el-button v-if="hasMore" :loading="loading" @click="loadOrders(true)">加载更多订单</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderCard from '../components/OrderCard.vue'
import { cancelOrder, confirmRefund, mockPaymentCallback, preparePayment, queryOrders, useOrder } from '../api/trade'
import type { OrderInfo, OrderStatus, TradeType } from '../types/order'

const orders = ref<OrderInfo[]>([])
const loading = ref(false)
const busyOrderId = ref<number>()
const filters = reactive<{
  tradeType: TradeType | ''
  orderStatus: OrderStatus | ''
}>({
  tradeType: '',
  orderStatus: '',
})

async function loadOrders() {
  loading.value = true
  try {
    const result = await queryOrders({
      tradeType: filters.tradeType,
      orderStatus: filters.orderStatus,
      pageSize: 20,
    })
    orders.value = result.orders
  } finally {
    loading.value = false
  }
}

function localDateTimeNow() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function buildOutTradeNo(order: OrderInfo) {
  return `WEB${order.orderId}${Date.now()}`
}

async function runOrderAction(order: OrderInfo, action: () => Promise<void>) {
  busyOrderId.value = order.orderId
  try {
    await action()
    await loadOrders()
  } finally {
    busyOrderId.value = undefined
  }
}

async function handlePay(order: OrderInfo) {
  await runOrderAction(order, async () => {
    const paymentOrder = await preparePayment(order.orderId)
    await mockPaymentCallback({
      payOrderNo: paymentOrder.payOrderNo,
      outTradeNo: buildOutTradeNo(order),
      payAmount: paymentOrder.payAmount,
      payTime: localDateTimeNow(),
    })
    ElMessage.success('支付成功')
  })
}

async function handleCancel(order: OrderInfo) {
  await ElMessageBox.confirm('取消后会释放已占用库存，确定取消这笔订单吗？', '取消订单', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning',
  })
  await runOrderAction(order, async () => {
    await cancelOrder(order.orderId)
    ElMessage.success('订单已取消')
  })
}

async function handleRefund(order: OrderInfo) {
  await ElMessageBox.confirm('退款会回退支付状态并触发库存补偿，确定继续吗？', '申请退款', {
    confirmButtonText: '确认退款',
    cancelButtonText: '暂不退款',
    type: 'warning',
  })
  await runOrderAction(order, async () => {
    await confirmRefund(order.orderId)
    ElMessage.success('退款完成')
  })
}

async function handleUse(order: OrderInfo) {
  await ElMessageBox.confirm('请确认用户已到店消费，核销后订单不可再次退款。', '到店核销', {
    confirmButtonText: '确认核销',
    cancelButtonText: '先不核销',
    type: 'info',
  })
  await runOrderAction(order, async () => {
    await useOrder(order.orderId)
    ElMessage.success('核销成功')
  })
}

onMounted(loadOrders)
</script>

<template>
  <section class="orders-view">
    <div class="section-head">
      <div>
        <p class="eyebrow">My Orders</p>
        <h1>我的订单</h1>
      </div>
      <div class="order-filters">
        <el-select v-model="filters.tradeType" placeholder="业务类型" clearable @change="loadOrders">
          <el-option label="普通购买" value="NORMAL" />
          <el-option label="拼团" value="GROUP_BUY" />
          <el-option label="秒杀" value="SECKILL" />
        </el-select>
        <el-select v-model="filters.orderStatus" placeholder="订单状态" clearable @change="loadOrders">
          <el-option label="待付款" value="WAIT_PAY" />
          <el-option label="已付款" value="PAID" />
          <el-option label="已核销" value="USED" />
          <el-option label="已取消" value="CANCELED" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
      </div>
    </div>

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
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Search, Tickets, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { operationConfirmRefund, queryOperationOrders } from '../api/trade'
import type { OrderInfo, OrderStatus, TradeType } from '../types/order'
import { money, shortDate } from '../utils/format'
import { foodImage, handleFoodImageError } from '../utils/images'

const loading = ref(false)
const orders = ref<OrderInfo[]>([])
const refunding = ref(false)
let requestVersion = 0
let active = true
onBeforeUnmount(() => { active = false; requestVersion++ })

const filters = reactive({
  userId: undefined as number | undefined,
  orderId: undefined as number | undefined,
  orderNo: '',
  tradeType: '' as TradeType | '',
  orderStatus: '' as OrderStatus | '',
  pageSize: 30,
})

async function loadOrders() {
  const version = ++requestVersion
  loading.value = true
  try {
    const result = await queryOperationOrders({
      userId: filters.userId,
      orderId: filters.orderId,
      orderNo: filters.orderNo.trim() || undefined,
      tradeType: filters.tradeType,
      orderStatus: filters.orderStatus,
      pageSize: filters.pageSize,
    })
    if (version === requestVersion) orders.value = result.orders
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function confirmRefund(order: OrderInfo) {
  if (refunding.value) return
  refunding.value = true
  try {
    await operationConfirmRefund(order.orderId, order.userId, '运营订单检索页确认退款')
    if (!active) return
    ElMessage.success('退款确认完成')
    await loadOrders()
  } catch { /* The HTTP interceptor reports request errors. */ }
  finally { refunding.value = false }
}

onMounted(loadOrders)
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">运营工作台 · 订单跟进</p>
        <h1>订单检索</h1>
        <p class="page-description">快速定位用户订单，核对交易状态与处理进度。</p>
      </div>
      <el-button :icon="Search" type="primary" :loading="loading" @click="loadOrders">查询</el-button>
    </div>

    <section class="operation-filter-panel order-search-panel">
      <div class="filter-field"><span>用户编号</span><el-input-number v-model="filters.userId" aria-label="用户编号" :min="1" controls-position="right" placeholder="输入用户 ID" /></div>
      <div class="filter-field"><span>订单编号</span><el-input-number v-model="filters.orderId" aria-label="订单编号" :min="1" controls-position="right" placeholder="输入订单 ID" /></div>
      <div class="filter-field"><span>订单号</span><el-input v-model="filters.orderNo" aria-label="订单号" clearable placeholder="输入完整订单号" @keyup.enter="loadOrders" /></div>
      <div class="filter-field"><span>交易类型</span><el-select v-model="filters.tradeType" aria-label="交易类型" clearable placeholder="全部类型">
        <el-option label="NORMAL" value="NORMAL" />
        <el-option label="GROUP_BUY" value="GROUP_BUY" />
        <el-option label="SECKILL" value="SECKILL" />
      </el-select></div>
      <div class="filter-field"><span>订单状态</span><el-select v-model="filters.orderStatus" aria-label="订单状态" clearable placeholder="全部状态">
        <el-option label="WAIT_PAY" value="WAIT_PAY" />
        <el-option label="PAID" value="PAID" />
        <el-option label="USED" value="USED" />
        <el-option label="CANCELED" value="CANCELED" />
        <el-option label="REFUNDED" value="REFUNDED" />
      </el-select></div>
      <div class="filter-field"><span>展示条数</span><el-input-number v-model="filters.pageSize" aria-label="展示条数" :min="10" :max="50" :step="10" controls-position="right" /></div>
    </section>

    <div v-loading="loading" class="operation-order-list">
      <article v-for="order in orders" :key="order.orderId" class="order-card">
        <img class="order-card__image" :src="foodImage(order.packageId, order.coverImageSnapshot)" :alt="order.packageNameSnapshot" loading="lazy" decoding="async" @error="handleFoodImageError($event, order.packageId)" />
        <div class="order-card__main">
          <div class="order-card__top">
            <div>
              <h3>{{ order.packageNameSnapshot || `订单 #${order.orderId}` }}</h3>
              <p>{{ order.shopNameSnapshot }}</p>
            </div>
            <el-tag>{{ order.orderStatus }}</el-tag>
          </div>
          <div class="order-card__meta">
            <span><el-icon><Tickets /></el-icon>{{ order.tradeType }}</span>
            <span>用户 {{ order.userId }}</span>
            <span>{{ shortDate(order.createTime) }}</span>
          </div>
          <div class="order-card__bottom">
            <strong>{{ money(order.payAmount) }}</strong>
            <span>{{ order.orderNo }}</span>
          </div>
          <div class="order-card__actions">
            <el-button
              v-if="order.orderStatus === 'PAID'"
              :icon="Wallet"
              type="danger"
              plain
              :disabled="refunding"
              @click="confirmRefund(order)"
            >
              确认退款
            </el-button>
          </div>
        </div>
      </article>
      <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />
    </div>
  </section>
</template>

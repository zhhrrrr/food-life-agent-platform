<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search, Tickets, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { operationConfirmRefund, queryOperationOrders } from '../api/trade'
import type { OrderInfo, OrderStatus, TradeType } from '../types/order'
import { money, shortDate } from '../utils/format'

const loading = ref(false)
const orders = ref<OrderInfo[]>([])

const filters = reactive({
  userId: undefined as number | undefined,
  orderId: undefined as number | undefined,
  orderNo: '',
  tradeType: '' as TradeType | '',
  orderStatus: '' as OrderStatus | '',
  pageSize: 30,
})

async function loadOrders() {
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
    orders.value = result.orders
  } finally {
    loading.value = false
  }
}

async function confirmRefund(order: OrderInfo) {
  await operationConfirmRefund(order.orderId, order.userId, '运营订单检索页确认退款')
  ElMessage.success('退款确认完成')
  await loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">Order Search</p>
        <h1>订单检索</h1>
      </div>
      <el-button :icon="Search" type="danger" :loading="loading" @click="loadOrders">查询</el-button>
    </div>

    <section class="operation-filter-panel order-search-panel">
      <el-input-number v-model="filters.userId" :min="1" controls-position="right" placeholder="用户 ID" />
      <el-input-number v-model="filters.orderId" :min="1" controls-position="right" placeholder="订单 ID" />
      <el-input v-model="filters.orderNo" clearable placeholder="订单号" @keyup.enter="loadOrders" />
      <el-select v-model="filters.tradeType" clearable placeholder="交易类型">
        <el-option label="NORMAL" value="NORMAL" />
        <el-option label="GROUP_BUY" value="GROUP_BUY" />
        <el-option label="SECKILL" value="SECKILL" />
      </el-select>
      <el-select v-model="filters.orderStatus" clearable placeholder="订单状态">
        <el-option label="WAIT_PAY" value="WAIT_PAY" />
        <el-option label="PAID" value="PAID" />
        <el-option label="USED" value="USED" />
        <el-option label="CANCELED" value="CANCELED" />
        <el-option label="REFUNDED" value="REFUNDED" />
      </el-select>
      <el-input-number v-model="filters.pageSize" :min="10" :max="50" :step="10" controls-position="right" />
    </section>

    <div v-loading="loading" class="operation-order-list">
      <article v-for="order in orders" :key="order.orderId" class="order-card">
        <img class="order-card__image" :src="order.coverImageSnapshot" alt="" />
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

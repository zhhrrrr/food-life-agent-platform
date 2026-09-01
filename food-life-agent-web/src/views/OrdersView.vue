<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import OrderCard from '../components/OrderCard.vue'
import { queryOrders } from '../api/trade'
import type { OrderInfo, OrderStatus, TradeType } from '../types/order'

const orders = ref<OrderInfo[]>([])
const loading = ref(false)
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
      <OrderCard v-for="order in orders" :key="order.orderId" :order="order" />
      <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />
    </div>
  </section>
</template>

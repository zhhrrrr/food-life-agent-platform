<script setup lang="ts">
import { Calendar, Check, Close, RefreshLeft, ShoppingBag, Wallet } from '@element-plus/icons-vue'
import type { OrderInfo } from '../types/order'
import { money, shortDate } from '../utils/format'
import { foodImage } from '../utils/images'

defineProps<{
  order: OrderInfo
  busy?: boolean
}>()

defineEmits<{
  pay: [order: OrderInfo]
  cancel: [order: OrderInfo]
  refund: [order: OrderInfo]
  use: [order: OrderInfo]
}>()

const statusMap: Record<string, string> = {
  WAIT_PAY: '待付款',
  PAID: '已付款',
  USED: '已核销',
  CANCELED: '已取消',
  REFUNDED: '已退款',
}

const typeMap: Record<string, string> = {
  NORMAL: '普通购买',
  GROUP_BUY: '拼团',
  SECKILL: '秒杀',
}
</script>

<template>
  <article class="order-card">
    <img class="order-card__image" :src="foodImage(order.packageId, order.coverImageSnapshot)" :alt="order.packageNameSnapshot" />
    <div class="order-card__main">
      <div class="order-card__top">
        <div>
          <h3>{{ order.packageNameSnapshot }}</h3>
          <p>{{ order.shopNameSnapshot }}</p>
        </div>
        <el-tag effect="plain" round>{{ statusMap[order.orderStatus] || order.orderStatus }}</el-tag>
      </div>
      <div class="order-card__meta">
        <span>
          <el-icon><ShoppingBag /></el-icon>
          {{ typeMap[order.tradeType] || order.tradeType }} · x{{ order.quantity }}
        </span>
        <span>
          <el-icon><Calendar /></el-icon>
          {{ shortDate(order.createTime) }}
        </span>
      </div>
      <div class="order-card__bottom">
        <span>{{ order.orderNo }}</span>
        <strong>{{ money(order.payAmount) }}</strong>
      </div>
      <div v-if="order.orderStatus === 'WAIT_PAY' || order.orderStatus === 'PAID'" class="order-card__actions">
        <template v-if="order.orderStatus === 'WAIT_PAY'">
          <el-button type="primary" :icon="Wallet" :loading="busy" @click="$emit('pay', order)">立即支付</el-button>
          <el-button :icon="Close" :disabled="busy" @click="$emit('cancel', order)">取消订单</el-button>
        </template>
        <template v-else>
          <el-button type="success" :icon="Check" :loading="busy" @click="$emit('use', order)">到店核销</el-button>
          <el-button :icon="RefreshLeft" :disabled="busy" @click="$emit('refund', order)">申请退款</el-button>
        </template>
      </div>
    </div>
  </article>
</template>

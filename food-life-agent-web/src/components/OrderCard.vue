<script setup lang="ts">
import { Calendar, ShoppingBag } from '@element-plus/icons-vue'
import type { OrderInfo } from '../types/order'
import { money, shortDate } from '../utils/format'
import { foodImage } from '../utils/images'

defineProps<{
  order: OrderInfo
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
    </div>
  </article>
</template>

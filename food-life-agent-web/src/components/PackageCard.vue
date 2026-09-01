<script setup lang="ts">
import { ShoppingBag, Tickets, Timer } from '@element-plus/icons-vue'
import type { MealPackage } from '../types/shop'
import { money } from '../utils/format'
import { foodImage } from '../utils/images'

defineProps<{
  item: MealPackage
  loading?: boolean
}>()

defineEmits<{
  normal: [MealPackage]
  group: [MealPackage]
  seckill: [MealPackage]
}>()
</script>

<template>
  <article class="package-card">
    <img class="package-card__image" :src="foodImage(item.id, item.coverImage)" :alt="item.name" />
    <div class="package-card__main">
      <div>
        <h3>{{ item.name }}</h3>
        <p>{{ item.description }}</p>
      </div>
      <div class="package-card__facts">
        <span>
          <el-icon><Tickets /></el-icon>
          库存 {{ item.stock }}
        </span>
        <span>
          <el-icon><Timer /></el-icon>
          {{ item.useRule }}
        </span>
      </div>
      <div class="package-card__footer">
        <div>
          <strong>{{ money(item.price) }}</strong>
          <del>{{ money(item.originalPrice) }}</del>
        </div>
        <div class="package-card__actions">
          <el-tooltip content="普通购买" placement="top">
            <el-button aria-label="普通购买" :loading="loading" :icon="ShoppingBag" round @click="$emit('normal', item)" />
          </el-tooltip>
          <el-button :loading="loading" type="danger" round @click="$emit('group', item)">拼团</el-button>
          <el-button :loading="loading" type="warning" round @click="$emit('seckill', item)">秒杀</el-button>
        </div>
      </div>
    </div>
  </article>
</template>

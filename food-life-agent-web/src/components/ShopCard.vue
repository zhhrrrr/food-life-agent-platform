<script setup lang="ts">
import { Location, StarFilled } from '@element-plus/icons-vue'
import type { ShopInfo } from '../types/shop'
import { money, score } from '../utils/format'
import { foodImage, handleFoodImageError } from '../utils/images'

defineProps<{
  shop: ShopInfo
}>()
</script>

<template>
  <router-link class="shop-card" :to="`/shops/${shop.id}`" :aria-label="shop.name">
    <img class="shop-card__image" :src="foodImage(shop.id, shop.images)" :alt="shop.name" loading="lazy" decoding="async" @error="handleFoodImageError($event, shop.id)" />
    <div class="shop-card__body">
      <div class="shop-card__title-row">
        <h3>{{ shop.name }}</h3>
        <span class="shop-card__score">
          <el-icon><StarFilled /></el-icon>
          {{ score(shop.score) }}
        </span>
      </div>
      <p class="shop-card__address">
        <el-icon><Location /></el-icon>
        {{ shop.area }} · {{ shop.address }}
      </p>
      <div class="shop-card__meta">
        <span>{{ money(shop.avgPrice) }}/人</span>
        <span>已售 {{ shop.sold }}</span>
        <span>{{ shop.comments }} 条评价</span>
      </div>
    </div>
  </router-link>
</template>

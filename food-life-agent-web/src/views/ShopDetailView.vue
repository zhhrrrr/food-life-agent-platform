<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, StarFilled } from '@element-plus/icons-vue'
import AgentDock from '../components/AgentDock.vue'
import PackageCard from '../components/PackageCard.vue'
import { favoriteShop, queryShopHomepage, unfavoriteShop } from '../api/business'
import { createGroupBuyOrder, createNormalOrder, createSeckillOrder, querySeckillActivities } from '../api/trade'
import type { MealPackage, ShopHomepage } from '../types/shop'
import { money, score, shortDate } from '../utils/format'
import { foodImage } from '../utils/images'

const route = useRoute()
const router = useRouter()
const homepage = ref<ShopHomepage | null>(null)
const loading = ref(false)
const ordering = ref(false)
const quantity = ref(1)
const teamId = ref('')

const shopId = computed(() => Number(route.params.id))

async function load() {
  loading.value = true
  try {
    homepage.value = await queryShopHomepage(shopId.value)
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!homepage.value) {
    return
  }
  if (homepage.value.favorite) {
    await unfavoriteShop(shopId.value)
    homepage.value.favorite = false
    ElMessage.success('已取消收藏')
  } else {
    await favoriteShop(shopId.value)
    homepage.value.favorite = true
    ElMessage.success('已收藏')
  }
}

async function orderNormal(item: MealPackage) {
  ordering.value = true
  try {
    const result = await createNormalOrder({ packageId: item.id, quantity: quantity.value })
    ElMessage.success(`订单已创建：${result.orderNo}`)
    router.push('/orders')
  } finally {
    ordering.value = false
  }
}

async function orderGroup(item: MealPackage) {
  ordering.value = true
  try {
    const result = await createGroupBuyOrder({
      packageId: item.id,
      quantity: quantity.value,
      teamId: teamId.value.trim() || undefined,
    })
    ElMessage.success(`拼团单已创建：${result.orderNo}`)
    router.push('/orders')
  } finally {
    ordering.value = false
  }
}

async function orderSeckill(item: MealPackage) {
  ordering.value = true
  try {
    const activities = await querySeckillActivities(item.id)
    const activity = activities.activities.find((candidate) => candidate.canBuy)
    if (!activity) {
      ElMessage.warning('当前套餐暂无可秒杀活动')
      return
    }
    const result = await createSeckillOrder({ activityId: activity.activityId, quantity: 1 })
    ElMessage.success(`秒杀单已创建：${result.orderNo}`)
    router.push('/orders')
  } finally {
    ordering.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="detail-view">
    <button class="text-button" type="button" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      返回
    </button>

    <template v-if="homepage">
      <div class="shop-hero-card">
        <img :src="foodImage(homepage.shop.id, homepage.shop.images)" :alt="homepage.shop.name" />
        <div class="shop-hero-card__content">
          <div class="shop-hero-card__top">
            <div>
              <p class="eyebrow">{{ homepage.shop.area }}</p>
              <h1>{{ homepage.shop.name }}</h1>
            </div>
            <el-button :icon="homepage.favorite ? StarFilled : Star" round @click="toggleFavorite">
              {{ homepage.favorite ? '已收藏' : '收藏' }}
            </el-button>
          </div>
          <div class="shop-hero-card__meta">
            <strong>{{ score(homepage.shop.score) }} 分</strong>
            <span>{{ homepage.shop.comments }} 条评价</span>
            <span>{{ money(homepage.shop.avgPrice) }}/人</span>
            <span>{{ homepage.shop.openHours }}</span>
          </div>
          <p>{{ homepage.shop.address }}</p>
        </div>
      </div>

      <div class="detail-grid">
        <section class="package-zone">
          <div class="section-head">
            <div>
              <p class="eyebrow">套餐买单</p>
              <h2>普通购买、拼团、秒杀</h2>
            </div>
            <div class="inline-controls">
              <el-input-number v-model="quantity" :min="1" :max="5" size="small" />
              <el-input v-model="teamId" size="small" placeholder="拼团队伍 ID" clearable />
            </div>
          </div>
          <div class="package-list">
            <PackageCard
              v-for="item in homepage.packages"
              :key="item.id"
              :item="item"
              :loading="ordering"
              @normal="orderNormal"
              @group="orderGroup"
              @seckill="orderSeckill"
            />
          </div>
        </section>

        <aside class="review-panel">
          <div class="section-head">
            <div>
              <p class="eyebrow">食客反馈</p>
              <h2>最近评价</h2>
            </div>
          </div>
          <article v-for="review in homepage.reviewSummary.latestReviews" :key="review.reviewId" class="review-item">
            <strong>{{ score(review.score * 10) }} 分</strong>
            <p>{{ review.content }}</p>
            <span>{{ shortDate(review.createTime) }}</span>
          </article>
          <el-empty v-if="homepage.reviewSummary.latestReviews.length === 0" description="暂无评价" />
        </aside>
      </div>

      <AgentDock :homepage="homepage" />
    </template>
  </section>
</template>

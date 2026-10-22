<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, StarFilled } from '@element-plus/icons-vue'
import AgentDock from '../components/AgentDock.vue'
import PackageCard from '../components/PackageCard.vue'
import { favoriteShop, queryShopHomepage, unfavoriteShop } from '../api/business'
import { createGroupBuyOrder, createNormalOrder, createSeckillOrder, querySeckillActivities } from '../api/trade'
import type { MealPackage, ShopHomepage } from '../types/shop'
import { money, score, shortDate } from '../utils/format'
import { foodImage, handleFoodImageError } from '../utils/images'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const homepage = ref<ShopHomepage | null>(null)
const loading = ref(false)
const ordering = ref(false)
const quantity = ref(1)
const teamId = ref('')
const favoriting = ref(false)
let active = true
let loadVersion = 0
onBeforeUnmount(() => { active = false; loadVersion++ })

const shopId = computed(() => Number(route.params.id))

async function load() {
  const version = ++loadVersion
  homepage.value = null
  loading.value = false
  if (!Number.isSafeInteger(shopId.value) || shopId.value < 1) return
  loading.value = true
  try {
    const result = await queryShopHomepage(shopId.value)
    if (active && version === loadVersion) homepage.value = result
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function toggleFavorite() {
  if (!homepage.value || favoriting.value) return
  const page = homepage.value
  const owner = auth.user?.id
  favoriting.value = true
  try {
    if (page.favorite) await unfavoriteShop(page.shop.id)
    else await favoriteShop(page.shop.id)
    if (!active || homepage.value !== page || auth.user?.id !== owner) return
    page.favorite = !page.favorite
    ElMessage.success(page.favorite ? '已收藏' : '已取消收藏')
  } catch { /* The HTTP interceptor reports request errors. */ }
  finally { favoriting.value = false }
}

async function placeOrder(create: () => Promise<{ orderNo: string } | undefined>) {
  if (ordering.value) return
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > 5) {
    ElMessage.warning('请选择 1 至 5 份套餐')
    return
  }
  const owner = auth.user?.id
  const id = shopId.value
  ordering.value = true
  try {
    const result = await create()
    if (!result || !active || owner !== auth.user?.id || id !== shopId.value) return
    ElMessage.success(`订单已创建：${result.orderNo}`)
    await router.push('/orders')
  } catch { /* The HTTP interceptor reports request errors. */ }
  finally { ordering.value = false }
}

async function orderNormal(item: MealPackage) {
  await placeOrder(() => createNormalOrder({ packageId: item.id, quantity: quantity.value }))
}

async function orderGroup(item: MealPackage) {
  await placeOrder(() => createGroupBuyOrder({
    packageId: item.id,
    quantity: quantity.value,
    teamId: teamId.value.trim() || undefined,
  }))
}

async function orderSeckill(item: MealPackage) {
  const owner = auth.user?.id
  const id = shopId.value
  await placeOrder(async () => {
    const activities = await querySeckillActivities(item.id)
    if (!active || owner !== auth.user?.id || id !== shopId.value) return
    const activity = activities.activities.find((candidate) => candidate.canBuy)
    if (!activity) {
      ElMessage.warning('当前套餐暂无可秒杀活动')
      return
    }
    return createSeckillOrder({ activityId: activity.activityId, quantity: 1 })
  })
}

watch(shopId, () => { quantity.value = 1; teamId.value = ''; void load() }, { immediate: true })
</script>

<template>
  <section v-loading="loading" class="detail-view">
    <button class="text-button" type="button" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      返回
    </button>

    <template v-if="homepage">
      <div class="shop-hero-card">
        <img :src="foodImage(homepage.shop.id, homepage.shop.images)" :alt="homepage.shop.name" @error="handleFoodImageError($event, homepage.shop.id)" />
        <div class="shop-hero-card__content">
          <div class="shop-hero-card__top">
            <div>
              <p class="eyebrow">{{ homepage.shop.area }}</p>
              <h1>{{ homepage.shop.name }}</h1>
            </div>
            <el-button :icon="homepage.favorite ? StarFilled : Star" :loading="favoriting" round @click="toggleFavorite">
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
              <el-input-number v-model="quantity" aria-label="购买数量" :min="1" :max="5" />
              <el-input v-model="teamId" aria-label="拼团队伍 ID" placeholder="拼团队伍 ID" clearable />
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
            <el-empty v-if="homepage.packages.length === 0" description="暂无在售套餐" />
          </div>
        </section>

        <aside class="detail-sidebar">
          <AgentDock :homepage="homepage" />
          <section class="review-panel">
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
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

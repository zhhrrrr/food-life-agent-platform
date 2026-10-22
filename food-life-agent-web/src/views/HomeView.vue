<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import AgentDock from '../components/AgentDock.vue'
import ShopCard from '../components/ShopCard.vue'
import { queryCategories, queryShopByName, queryShopsByCategory } from '../api/business'
import type { ShopCategory, ShopInfo } from '../types/shop'

const categories = ref<ShopCategory[]>([])
const shops = ref<ShopInfo[]>([])
const activeCategory = ref<number>()
const keyword = ref('')
const loading = ref(false)
const hasMore = ref(false)
// The business listing API returns ten shops per page, without a total count.
const pageSize = 10
let currentPage = 0
let appliedQuery: { keyword: string; categoryId?: number } = { keyword: '' }
let requestVersion = 0
let active = true
onBeforeUnmount(() => { active = false; requestVersion++ })

async function loadCategories() {
  const result = await queryCategories()
  if (!active) return
  categories.value = result
  activeCategory.value = categories.value[0]?.id
}

async function loadShops(append = false) {
  const loadMore = append === true
  if (loadMore && (loading.value || !hasMore.value)) return
  const version = ++requestVersion
  if (!loadMore) {
    appliedQuery = { keyword: keyword.value.trim(), categoryId: activeCategory.value }
    currentPage = 0
    hasMore.value = false
  }
  const query = appliedQuery
  const page = loadMore ? currentPage + 1 : 1
  loading.value = true
  try {
    const result = query.keyword
      ? await queryShopByName(query.keyword, page)
      : query.categoryId ? await queryShopsByCategory(query.categoryId, page) : []
    if (!active || version !== requestVersion) return
    shops.value = loadMore ? [...shops.value, ...result] : result
    currentPage = page
    hasMore.value = result.length === pageSize
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

async function chooseCategory(id: number) {
  keyword.value = ''
  activeCategory.value = id
  await loadShops()
}

onMounted(async () => {
  try {
    await loadCategories()
    if (active) await loadShops()
  } catch { /* The HTTP interceptor reports request errors. */ }
})
</script>

<template>
  <section class="home-view">
    <div class="home-hero">
      <div>
        <p class="eyebrow">发现附近的好味道</p>
        <h1>找好店，拼好价，把一顿饭安排明白。</h1>
        <p class="page-description">从附近的好店，到适合你的那一餐。</p>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" size="large" aria-label="搜索店铺" placeholder="火锅、烧烤、甜品" :prefix-icon="Search" @keyup.enter="loadShops" />
        <el-button type="primary" size="large" @click="loadShops()">搜索</el-button>
      </div>
    </div>

    <div class="home-grid">
      <section>
        <div class="category-strip" aria-label="美食分类">
          <button
            v-for="item in categories"
            :key="item.id"
            type="button"
            :class="{ active: activeCategory === item.id && !keyword }"
            :aria-pressed="activeCategory === item.id && !keyword"
            @click="chooseCategory(item.id)"
          >
            {{ item.name }}
          </button>
        </div>

        <div v-loading="loading" class="shop-grid">
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" />
          <el-empty v-if="!loading && shops.length === 0" description="暂无店铺" />
        </div>
        <el-button v-if="hasMore" :loading="loading" @click="loadShops(true)">加载更多店铺</el-button>
      </section>

      <AgentDock />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

async function loadCategories() {
  categories.value = await queryCategories()
  activeCategory.value = categories.value[0]?.id
}

async function loadShops() {
  loading.value = true
  try {
    if (keyword.value.trim()) {
      shops.value = await queryShopByName(keyword.value.trim())
      return
    }
    if (activeCategory.value) {
      shops.value = await queryShopsByCategory(activeCategory.value)
    }
  } finally {
    loading.value = false
  }
}

async function chooseCategory(id: number) {
  keyword.value = ''
  activeCategory.value = id
  await loadShops()
}

onMounted(async () => {
  await loadCategories()
  await loadShops()
})
</script>

<template>
  <section class="home-view">
    <div class="home-hero">
      <div>
        <p class="eyebrow">FoodLife Picks</p>
        <h1>找好店，拼好价，把一顿饭安排明白。</h1>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" size="large" placeholder="火锅、烧烤、甜品" :prefix-icon="Search" @keyup.enter="loadShops" />
        <el-button type="danger" size="large" @click="loadShops">搜索</el-button>
      </div>
    </div>

    <div class="home-grid">
      <section>
        <div class="category-strip">
          <button
            v-for="item in categories"
            :key="item.id"
            type="button"
            :class="{ active: activeCategory === item.id && !keyword }"
            @click="chooseCategory(item.id)"
          >
            {{ item.name }}
          </button>
        </div>

        <div v-loading="loading" class="shop-grid">
          <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" />
          <el-empty v-if="!loading && shops.length === 0" description="暂无店铺" />
        </div>
      </section>

      <AgentDock />
    </div>
  </section>
</template>

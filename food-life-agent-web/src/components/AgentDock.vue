<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, MagicStick } from '@element-plus/icons-vue'
import type { ShopHomepage } from '../types/shop'

const props = defineProps<{
  homepage?: ShopHomepage | null
}>()

const hints = computed(() => {
  if (!props.homepage) {
    return ['今晚想吃什么', '找高性价比套餐', '看看我的订单']
  }
  const shop = props.homepage.shop
  const pack = props.homepage.packages[0]
  return [
    `${shop.name} 适合几人吃`,
    pack ? `${pack.name} 怎么买划算` : '附近还有哪些套餐',
    props.homepage.favorite ? '查看我的收藏店铺' : '收藏这家店',
  ]
})
</script>

<template>
  <aside class="agent-dock">
    <div class="agent-dock__head">
      <el-icon><ChatDotRound /></el-icon>
      <span>美食拼团 Agent</span>
    </div>
    <p>帮你挑套餐、比价格、跟进订单状态。</p>
    <div class="agent-dock__chips">
      <button v-for="hint in hints" :key="hint" type="button">
        <el-icon><MagicStick /></el-icon>
        {{ hint }}
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bowl,
  Box,
  ChatLineSquare,
  DocumentChecked,
  HomeFilled,
  MessageBox,
  Search,
  SwitchButton,
  Tickets,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
watch(() => auth.token, token => { if (!token) void router.replace('/login') })
const canViewOperations = computed(() => {
  const role = auth.user?.role?.toUpperCase()
  return role === 'ADMIN' || role === 'OPERATOR'
})

onMounted(() => {
  // The HTTP interceptor invalidates only a rejected credential. A network
  // failure must not sign out this account or a newer login.
  auth.fetchMe().catch(() => {})
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand" type="button" @click="router.push('/')">
        <span class="brand__mark">
          <el-icon><Bowl /></el-icon>
        </span>
        <span>
          <strong>FoodLife</strong>
          <small>美食拼团 Agent</small>
        </span>
      </button>
      <nav class="topbar__nav" aria-label="主导航">
        <router-link to="/" active-class="" exact-active-class="is-active">
          <el-icon><HomeFilled /></el-icon>
          首页
        </router-link>
        <router-link to="/orders" active-class="" exact-active-class="is-active">
          <el-icon><Tickets /></el-icon>
          订单
        </router-link>
        <router-link v-if="canViewOperations" to="/operations/orders" active-class="" :class="{ 'is-active': $route.path.startsWith('/operations') }">
          <el-icon><DocumentChecked /></el-icon>
          运营
        </router-link>
      </nav>
      <div class="topbar__user">
        <el-avatar :size="36" :src="auth.user?.icon">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <span class="hidden-sm-and-down">{{ auth.user?.nickName || 'Foodie' }}</span>
        <el-tooltip content="退出登录" placement="bottom">
          <el-button :icon="SwitchButton" aria-label="退出登录" circle @click="logout" />
        </el-tooltip>
      </div>
    </header>

    <nav v-if="canViewOperations && $route.path.startsWith('/operations')" class="operations-nav" aria-label="运营功能">
      <router-link to="/operations/orders"><el-icon><Search /></el-icon>订单检索</router-link>
      <router-link to="/operations/stock"><el-icon><Box /></el-icon>库存调整</router-link>
      <router-link to="/operations/refunds"><el-icon><Wallet /></el-icon>退款确认</router-link>
      <router-link to="/operations/audit"><el-icon><ChatLineSquare /></el-icon>审计日志</router-link>
      <router-link to="/operations/messages"><el-icon><MessageBox /></el-icon>异常消息</router-link>
    </nav>

    <main>
      <router-view v-if="auth.token" :key="auth.token" />
    </main>
  </div>
</template>

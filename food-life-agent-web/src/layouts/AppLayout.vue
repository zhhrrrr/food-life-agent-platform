<script setup lang="ts">
import { computed, onMounted } from 'vue'
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
const canViewOperations = computed(() => {
  const role = auth.user?.role?.toUpperCase()
  return role === 'ADMIN' || role === 'OPERATOR'
})

onMounted(() => {
  auth.fetchMe().catch(() => auth.logout())
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
      <nav class="topbar__nav">
        <router-link to="/">
          <el-icon><HomeFilled /></el-icon>
          首页
        </router-link>
        <router-link to="/orders">
          <el-icon><Tickets /></el-icon>
          订单
        </router-link>
        <el-dropdown v-if="canViewOperations" trigger="click">
          <button class="nav-menu-button" type="button">
            <el-icon><DocumentChecked /></el-icon>
            运营
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/operations/audit')">
                <el-icon><ChatLineSquare /></el-icon>
                审计日志
              </el-dropdown-item>
              <el-dropdown-item @click="router.push('/operations/orders')">
                <el-icon><Search /></el-icon>
                订单检索
              </el-dropdown-item>
              <el-dropdown-item @click="router.push('/operations/stock')">
                <el-icon><Box /></el-icon>
                库存调整
              </el-dropdown-item>
              <el-dropdown-item @click="router.push('/operations/refunds')">
                <el-icon><Wallet /></el-icon>
                退款确认
              </el-dropdown-item>
              <el-dropdown-item @click="router.push('/operations/messages')">
                <el-icon><MessageBox /></el-icon>
                异常消息
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>
      <div class="topbar__user">
        <el-avatar :size="36" :src="auth.user?.icon">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <span class="hidden-sm-and-down">{{ auth.user?.nickName || 'Foodie' }}</span>
        <el-tooltip content="退出登录" placement="bottom">
          <el-button :icon="SwitchButton" circle @click="logout" />
        </el-tooltip>
      </div>
    </header>

    <main>
      <router-view />
    </main>
  </div>
</template>

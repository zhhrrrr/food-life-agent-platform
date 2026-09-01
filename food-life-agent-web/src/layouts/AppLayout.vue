<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bowl, HomeFilled, SwitchButton, Tickets, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

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

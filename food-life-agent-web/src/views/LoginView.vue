<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bowl, Message, Phone } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({
  phone: '',
  code: '',
})

const sending = ref(false)

async function sendCode() {
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请输入正确手机号')
    return
  }
  sending.value = true
  try {
    await auth.sendCode(form.phone)
    ElMessage.success('验证码已发送')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!form.phone || !form.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  await auth.loginByCode(form.phone, form.code)
  ElMessage.success('欢迎回来')
  router.push((route.query.redirect as string) || '/')
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual">
      <div class="login-visual__plate">
        <img src="https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=1200&q=80" alt="hot food" />
      </div>
      <div>
        <div class="brand brand--light">
          <span class="brand__mark">
            <el-icon><Bowl /></el-icon>
          </span>
          <span>
            <strong>FoodLife</strong>
            <small>美食拼团 Agent</small>
          </span>
        </div>
        <h1>今晚吃点让人开心的。</h1>
        <p>发现附近好店，拼团下单，跟进订单状态。</p>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel__inner">
        <p class="eyebrow">手机号登录</p>
        <h2>进入美食生活</h2>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" size="large" maxlength="11" placeholder="13900000000" :prefix-icon="Phone" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="form.code" size="large" maxlength="6" placeholder="6 位验证码" :prefix-icon="Message" />
              <el-button size="large" :loading="sending" @click="sendCode">获取</el-button>
            </div>
          </el-form-item>
          <el-button class="login-button" type="danger" size="large" :loading="auth.loading" @click="submit">
            登录
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

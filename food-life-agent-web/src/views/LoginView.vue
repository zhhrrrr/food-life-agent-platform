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
  if (sending.value) return
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请输入正确手机号')
    return
  }
  sending.value = true
  try {
    await auth.sendCode(form.phone)
    ElMessage.success('验证码已发送')
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (auth.loading) return
  if (!/^1\d{10}$/.test(form.phone) || !/^\d{6}$/.test(form.code)) {
    ElMessage.warning('请填写正确的手机号和 6 位验证码')
    return
  }
  try {
    await auth.loginByCode(form.phone, form.code)
    ElMessage.success('欢迎回来')
    const redirect = route.query.redirect
    await router.push(typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
  } catch { /* The HTTP interceptor reports request errors. */ }
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual">
      <div class="login-visual__plate">
        <img src="/images/meal-1.jpg" alt="丰盛餐桌上的美食" fetchpriority="high" />
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
        <p class="page-description">好吃的、划算的，和你一起慢慢发现。</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="手机号">
            <el-input v-model="form.phone" size="large" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="13900000000" :prefix-icon="Phone" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="form.code" size="large" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6 位验证码" :prefix-icon="Message" />
              <el-button size="large" :loading="sending" @click="sendCode">获取</el-button>
            </div>
          </el-form-item>
          <el-button class="login-button" type="primary" native-type="submit" size="large" :loading="auth.loading">
            登录
          </el-button>
        </el-form>
        <p class="login-note">使用手机号验证码，登录你的美食生活。</p>
      </div>
    </section>
  </main>
</template>

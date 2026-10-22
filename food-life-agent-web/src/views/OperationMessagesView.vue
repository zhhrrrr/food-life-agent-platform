<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Refresh, Search, UploadFilled, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { queryOperationMqMessages, queryOperationMqQueues, republishOperationMqMessage } from '../api/trade'
import type { OperationMqMessage, OperationMqQueue } from '../types/order'
import { shortDate, trimOrUndefined } from '../utils/format'

const loading = ref(false)
const queueLoading = ref(false)
const messages = ref<OperationMqMessage[]>([])
const queues = ref<OperationMqQueue[]>([])
const republishing = ref(false)
let messageVersion = 0
let queueVersion = 0
let active = true
onBeforeUnmount(() => { active = false; messageVersion++; queueVersion++ })

const filters = reactive({
  messageStatus: 'FAILED',
  messageType: '',
  bizId: '',
  limit: 50,
})

async function loadMessages() {
  const version = ++messageVersion
  loading.value = true
  try {
    const result = await queryOperationMqMessages({
      messageStatus: trimOrUndefined(filters.messageStatus),
      messageType: trimOrUndefined(filters.messageType),
      bizId: trimOrUndefined(filters.bizId),
      limit: filters.limit,
    })
    if (version === messageVersion) messages.value = result.messages
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === messageVersion) loading.value = false
  }
}

async function loadQueues() {
  const version = ++queueVersion
  queueLoading.value = true
  try {
    const result = await queryOperationMqQueues()
    if (version === queueVersion) queues.value = result.queues
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === queueVersion) queueLoading.value = false
  }
}

async function republish(item: OperationMqMessage) {
  if (republishing.value) return
  republishing.value = true
  try {
    const result = await republishOperationMqMessage(item.messageId)
    if (!active) return
    if (result.accepted) {
      ElMessage.success('重投已提交')
      await loadMessages()
    } else ElMessage.info(result.remark || '消息未接受重投，请刷新后核对状态')
  } catch { /* The HTTP interceptor reports request errors. */ }
  finally { republishing.value = false }
}

onMounted(() => {
  loadMessages()
  loadQueues()
})
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">运营工作台 · 消息跟进</p>
        <h1>异常消息处理</h1>
        <p class="page-description">关注队列积压与失败消息，及时跟进重投结果。</p>
      </div>
      <el-button :icon="Refresh" :loading="queueLoading" @click="loadQueues">刷新队列</el-button>
    </div>

    <section class="queue-strip" v-loading="queueLoading">
      <article v-for="queue in queues" :key="queue.queueName" class="queue-pill" :class="{ alert: queue.backlogAlarm }">
        <el-icon><Warning /></el-icon>
        <strong>{{ queue.queueName }}</strong>
        <span>{{ queue.messageCount }} / {{ queue.backlogThreshold }}</span>
      </article>
    </section>

    <section class="operation-filter-panel">
      <div class="filter-field"><span>消息状态</span><el-select v-model="filters.messageStatus" aria-label="消息状态" clearable placeholder="全部状态">
        <el-option label="FAILED" value="FAILED" />
        <el-option label="INIT" value="INIT" />
        <el-option label="PROCESSING" value="PROCESSING" />
        <el-option label="SUCCESS" value="SUCCESS" />
      </el-select></div>
      <div class="filter-field"><span>消息类型</span><el-input v-model="filters.messageType" aria-label="消息类型" clearable placeholder="输入消息类型" @keyup.enter="loadMessages" /></div>
      <div class="filter-field"><span>业务编号</span><el-input v-model="filters.bizId" aria-label="业务编号" clearable placeholder="输入业务 ID" @keyup.enter="loadMessages" /></div>
      <div class="filter-field"><span>展示条数</span><el-input-number v-model="filters.limit" aria-label="展示条数" :min="10" :max="100" :step="10" controls-position="right" /></div>
      <div class="filter-actions"><el-button :icon="Search" type="primary" :loading="loading" @click="loadMessages">查询</el-button></div>
    </section>

    <div v-loading="loading" class="message-list">
      <article v-for="item in messages" :key="item.id" class="message-card">
        <div class="message-card__top">
          <div>
            <h3>{{ item.messageType }}</h3>
            <p>{{ item.messageId }}</p>
          </div>
          <el-tag :type="item.messageStatus === 'FAILED' ? 'danger' : 'info'">{{ item.messageStatus }}</el-tag>
        </div>
        <div class="audit-meta">
          <span>业务 {{ item.bizId }}</span>
          <span>重试 {{ item.retryCount }}/{{ item.maxRetryCount }}</span>
          <span>{{ shortDate(item.updateTime) }}</span>
        </div>
        <pre v-if="item.failReason">{{ item.failReason }}</pre>
        <el-button :icon="UploadFilled" type="danger" plain :disabled="republishing" @click="republish(item)">重投</el-button>
      </article>
      <el-empty v-if="!loading && messages.length === 0" description="暂无消息" />
    </div>
  </section>
</template>

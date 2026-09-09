<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search, UploadFilled, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { queryOperationMqMessages, queryOperationMqQueues, republishOperationMqMessage } from '../api/trade'
import type { OperationMqMessage, OperationMqQueue } from '../types/order'
import { shortDate } from '../utils/format'

const loading = ref(false)
const queueLoading = ref(false)
const messages = ref<OperationMqMessage[]>([])
const queues = ref<OperationMqQueue[]>([])

const filters = reactive({
  messageStatus: 'FAILED',
  messageType: '',
  bizId: '',
  limit: 50,
})

function trimOrUndefined(value: string) {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}

async function loadMessages() {
  loading.value = true
  try {
    const result = await queryOperationMqMessages({
      messageStatus: trimOrUndefined(filters.messageStatus),
      messageType: trimOrUndefined(filters.messageType),
      bizId: trimOrUndefined(filters.bizId),
      limit: filters.limit,
    })
    messages.value = result.messages
  } finally {
    loading.value = false
  }
}

async function loadQueues() {
  queueLoading.value = true
  try {
    const result = await queryOperationMqQueues()
    queues.value = result.queues
  } finally {
    queueLoading.value = false
  }
}

async function republish(item: OperationMqMessage) {
  const result = await republishOperationMqMessage(item.messageId)
  if (result.accepted) {
    ElMessage.success('重投已提交')
    await loadMessages()
  }
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
        <p class="eyebrow">Message Recovery</p>
        <h1>异常消息处理</h1>
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
      <el-select v-model="filters.messageStatus" clearable placeholder="消息状态">
        <el-option label="FAILED" value="FAILED" />
        <el-option label="INIT" value="INIT" />
        <el-option label="PROCESSING" value="PROCESSING" />
        <el-option label="SUCCESS" value="SUCCESS" />
      </el-select>
      <el-input v-model="filters.messageType" clearable placeholder="消息类型" @keyup.enter="loadMessages" />
      <el-input v-model="filters.bizId" clearable placeholder="业务 ID" @keyup.enter="loadMessages" />
      <el-input-number v-model="filters.limit" :min="10" :max="100" :step="10" controls-position="right" />
      <el-button :icon="Search" type="danger" :loading="loading" @click="loadMessages">查询</el-button>
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
          <span>biz {{ item.bizId }}</span>
          <span>retry {{ item.retryCount }}/{{ item.maxRetryCount }}</span>
          <span>{{ shortDate(item.updateTime) }}</span>
        </div>
        <pre v-if="item.failReason">{{ item.failReason }}</pre>
        <el-button :icon="UploadFilled" type="danger" plain @click="republish(item)">重投</el-button>
      </article>
      <el-empty v-if="!loading && messages.length === 0" description="暂无消息" />
    </div>
  </section>
</template>

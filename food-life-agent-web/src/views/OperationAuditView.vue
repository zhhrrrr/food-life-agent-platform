<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Close, DocumentChecked, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { queryOperationAuditLogs } from '../api/trade'
import type { OperationAuditLog, OperationAuditQuery } from '../types/order'
import { shortDate } from '../utils/format'

const logs = ref<OperationAuditLog[]>([])
const loading = ref(false)

const filters = reactive({
  traceId: '',
  operatorId: '',
  operationType: '',
  bizType: '',
  bizId: '',
  limit: 50,
})

const operationTypes = [
  'PAYMENT_PREPARE',
  'PAYMENT_CALLBACK_LOCAL',
  'ORDER_PAY_LOCAL',
  'ORDER_CANCEL',
  'USER_REFUND_APPLY',
  'OPERATION_REFUND_CONFIRM',
  'ORDER_USE_LOCAL',
  'OPERATION_STOCK_ADJUST',
]

const bizTypes = ['ORDER', 'PAYMENT_ORDER', 'PACKAGE_STOCK']

function trimOrUndefined(value: string) {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}

function buildQuery(): OperationAuditQuery {
  const operatorId = Number(filters.operatorId)
  return {
    traceId: trimOrUndefined(filters.traceId),
    operatorId: Number.isFinite(operatorId) && operatorId > 0 ? operatorId : undefined,
    operationType: trimOrUndefined(filters.operationType),
    bizType: trimOrUndefined(filters.bizType),
    bizId: trimOrUndefined(filters.bizId),
    limit: filters.limit,
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const result = await queryOperationAuditLogs(buildQuery())
    logs.value = result.logs
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.traceId = ''
  filters.operatorId = ''
  filters.operationType = ''
  filters.bizType = ''
  filters.bizId = ''
  filters.limit = 50
  loadLogs()
}

async function copyTraceId(traceId?: string) {
  if (!traceId) {
    return
  }
  await navigator.clipboard.writeText(traceId)
  ElMessage.success('traceId 已复制')
}

function compact(value?: string) {
  if (!value) {
    return '无'
  }
  return value.length > 240 ? `${value.slice(0, 240)}...` : value
}

function statusClass(status: string) {
  return status === 'SUCCESS' ? 'success' : 'warning'
}

onMounted(loadLogs)
</script>

<template>
  <section class="operation-audit-view">
    <div class="section-head audit-head">
      <div>
        <p class="eyebrow">Operation Audit</p>
        <h1>运营审计</h1>
      </div>
      <el-button :icon="Refresh" type="danger" size="large" :loading="loading" @click="loadLogs">
        刷新
      </el-button>
    </div>

    <section class="audit-filter-panel">
      <el-input v-model="filters.traceId" clearable placeholder="traceId" @keyup.enter="loadLogs" />
      <el-input v-model="filters.operatorId" clearable placeholder="操作人 ID" @keyup.enter="loadLogs" />
      <el-select v-model="filters.operationType" clearable filterable placeholder="操作类型">
        <el-option v-for="item in operationTypes" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filters.bizType" clearable filterable placeholder="业务类型">
        <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
      </el-select>
      <el-input v-model="filters.bizId" clearable placeholder="业务 ID" @keyup.enter="loadLogs" />
      <el-input-number v-model="filters.limit" :min="10" :max="200" :step="10" controls-position="right" />
      <div class="audit-filter-panel__actions">
        <el-button :icon="Search" type="danger" :loading="loading" @click="loadLogs">查询</el-button>
        <el-button :icon="Close" @click="resetFilters">重置</el-button>
      </div>
    </section>

    <div v-loading="loading" class="audit-timeline">
      <article v-for="item in logs" :key="item.id" class="audit-card">
        <div class="audit-card__icon">
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <div class="audit-card__main">
          <div class="audit-card__top">
            <div>
              <h3>{{ item.operationType }}</h3>
              <p>{{ item.remark || item.bizType }}</p>
            </div>
            <span class="audit-status" :class="statusClass(item.operationStatus)">
              {{ item.operationStatus }}
            </span>
          </div>

          <div class="audit-meta">
            <span>{{ shortDate(item.createTime) }}</span>
            <span>操作人 {{ item.operatorId || '-' }}</span>
            <span>{{ item.operatorRole || 'USER' }}</span>
            <span>{{ item.bizType }} #{{ item.bizId }}</span>
          </div>

          <button class="audit-trace" type="button" @click="copyTraceId(item.traceId)">
            {{ item.traceId || 'no-trace-id' }}
          </button>

          <el-collapse class="audit-payload">
            <el-collapse-item title="请求快照" name="request">
              <pre>{{ compact(item.requestContent) }}</pre>
            </el-collapse-item>
            <el-collapse-item title="响应快照" name="response">
              <pre>{{ compact(item.responseContent) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </article>
      <el-empty v-if="!loading && logs.length === 0" description="暂无审计记录" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Close, DocumentChecked, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { queryOperationAuditLogs } from '../api/trade'
import type { OperationAuditLog, OperationAuditQuery } from '../types/order'
import { shortDate, trimOrUndefined } from '../utils/format'

const logs = ref<OperationAuditLog[]>([])
const loading = ref(false)
let requestVersion = 0
onBeforeUnmount(() => { requestVersion++ })

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
  const version = ++requestVersion
  loading.value = true
  try {
    const result = await queryOperationAuditLogs(buildQuery())
    if (version === requestVersion) logs.value = result.logs
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    if (version === requestVersion) loading.value = false
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
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('traceId 已复制')
  } catch { ElMessage.warning('无法访问剪贴板，请手动复制 traceId') }
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
        <p class="eyebrow">运营工作台 · 操作追踪</p>
        <h1>运营审计</h1>
        <p class="page-description">按操作与业务线索，查看每一次处理记录。</p>
      </div>
      <el-button :icon="Refresh" type="primary" :loading="loading" @click="loadLogs">
        刷新
      </el-button>
    </div>

    <section class="audit-filter-panel">
      <div class="filter-field"><span>追踪编号</span><el-input v-model="filters.traceId" aria-label="追踪编号" clearable placeholder="输入 traceId" @keyup.enter="loadLogs" /></div>
      <div class="filter-field"><span>操作人</span><el-input v-model="filters.operatorId" aria-label="操作人 ID" clearable placeholder="输入操作人 ID" @keyup.enter="loadLogs" /></div>
      <div class="filter-field"><span>操作类型</span><el-select v-model="filters.operationType" aria-label="操作类型" clearable filterable placeholder="全部操作">
          <el-option v-for="item in operationTypes" :key="item" :label="item" :value="item" />
      </el-select></div>
      <div class="filter-field"><span>业务类型</span><el-select v-model="filters.bizType" aria-label="业务类型" clearable filterable placeholder="全部类型">
          <el-option v-for="item in bizTypes" :key="item" :label="item" :value="item" />
      </el-select></div>
      <div class="filter-field"><span>业务编号</span><el-input v-model="filters.bizId" aria-label="业务编号" clearable placeholder="输入业务 ID" @keyup.enter="loadLogs" /></div>
      <div class="filter-field"><span>展示条数</span><el-input-number v-model="filters.limit" aria-label="展示条数" :min="10" :max="200" :step="10" controls-position="right" /></div>
      <div class="filter-actions">
        <el-button :icon="Close" @click="resetFilters">重置</el-button>
        <el-button :icon="Search" type="primary" :loading="loading" @click="loadLogs">查询</el-button>
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

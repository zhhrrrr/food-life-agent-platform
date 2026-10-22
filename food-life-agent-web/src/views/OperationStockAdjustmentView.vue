<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { Box, Refresh, SetUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { preheatBusinessCache } from '../api/business'
import { operationAdjustPackageStock } from '../api/trade'
import type { OperationPackageStockAdjustResponse } from '../types/order'

const loading = ref(false)
const preheating = ref(false)
const result = ref<OperationPackageStockAdjustResponse>()
let active = true
onBeforeUnmount(() => { active = false })

const form = reactive({
  packageId: undefined as number | undefined,
  adjustQuantity: 10,
  reason: '运营手动调整库存',
  operationId: `OPS-STOCK-${crypto.randomUUID()}`,
})

async function submit() {
  if (loading.value) return
  if (!Number.isSafeInteger(form.packageId) || !form.packageId || form.packageId < 1
    || !Number.isSafeInteger(form.adjustQuantity) || !form.adjustQuantity
    || Math.abs(form.adjustQuantity) > 9999 || !form.reason.trim() || !form.operationId.trim()) {
    ElMessage.warning('请填写有效的套餐 ID、非 0 整数调整量、原因和操作号')
    return
  }
  loading.value = true
  try {
    const adjusted = await operationAdjustPackageStock({
      packageId: form.packageId,
      adjustQuantity: form.adjustQuantity,
      reason: form.reason,
      operationId: form.operationId,
    })
    if (!active) return
    result.value = adjusted
    ElMessage.success('库存调整完成')
    form.operationId = `OPS-STOCK-${crypto.randomUUID()}`
  } catch {
    // Preserve the operation ID on failure so retrying remains idempotent.
  } finally {
    loading.value = false
  }
}

async function preheat() {
  if (preheating.value) return
  if (!form.packageId) {
    ElMessage.warning('请先填写套餐 ID')
    return
  }
  preheating.value = true
  try {
    await preheatBusinessCache({ packageId: form.packageId })
    if (!active) return
    ElMessage.success('套餐交易快照缓存已预热')
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    preheating.value = false
  }
}
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">运营工作台 · 套餐管理</p>
        <h1>库存调整</h1>
        <p class="page-description">记录每次库存变更，让套餐供应有据可查。</p>
      </div>
      <el-button :icon="Refresh" :loading="preheating" @click="preheat">预热缓存</el-button>
    </div>

    <section class="operation-workbench">
      <div class="operation-panel">
        <div class="panel-title">
          <el-icon><SetUp /></el-icon>
          <h2>调整单</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="套餐 ID">
            <el-input-number v-model="form.packageId" aria-label="套餐 ID" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="调整数量">
            <el-input-number v-model="form.adjustQuantity" aria-label="调整数量" :min="-9999" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="调整原因">
            <el-input v-model="form.reason" maxlength="120" show-word-limit />
          </el-form-item>
          <el-form-item label="幂等操作号">
            <el-input v-model="form.operationId" />
          </el-form-item>
          <el-button :icon="Box" type="primary" size="large" :loading="loading" @click="submit">提交调整</el-button>
        </el-form>
      </div>

      <div class="operation-result">
        <p class="eyebrow">处理结果</p>
        <h2>{{ result ? '调整成功' : '等待提交' }}</h2>
        <p v-if="!result">提交库存调整后，在这里查看最新库存与处理状态。</p>
        <div v-if="result" class="metric-grid">
          <span>套餐 #{{ result.packageId }}</span>
          <span>库存 {{ result.stock }}</span>
          <span>已售 {{ result.sold }}</span>
          <span>{{ result.txStatus }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

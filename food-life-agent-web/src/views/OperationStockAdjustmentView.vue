<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Box, Refresh, SetUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { preheatBusinessCache } from '../api/business'
import { operationAdjustPackageStock } from '../api/trade'
import type { OperationPackageStockAdjustResponse } from '../types/order'

const loading = ref(false)
const preheating = ref(false)
const result = ref<OperationPackageStockAdjustResponse>()

const form = reactive({
  packageId: undefined as number | undefined,
  adjustQuantity: 10,
  reason: '运营手动调整库存',
  operationId: `OPS-STOCK-${Date.now()}`,
})

async function submit() {
  if (!form.packageId || form.adjustQuantity === 0) {
    ElMessage.warning('请填写套餐 ID 和非 0 调整数')
    return
  }
  loading.value = true
  try {
    result.value = await operationAdjustPackageStock({
      packageId: form.packageId,
      adjustQuantity: form.adjustQuantity,
      reason: form.reason,
      operationId: form.operationId,
    })
    ElMessage.success('库存调整完成')
    form.operationId = `OPS-STOCK-${Date.now()}`
  } finally {
    loading.value = false
  }
}

async function preheat() {
  if (!form.packageId) {
    ElMessage.warning('请先填写套餐 ID')
    return
  }
  preheating.value = true
  try {
    await preheatBusinessCache({ packageId: form.packageId })
    ElMessage.success('套餐交易快照缓存已预热')
  } finally {
    preheating.value = false
  }
}
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">Stock Operation</p>
        <h1>库存调整</h1>
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
            <el-input-number v-model="form.packageId" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="调整数量">
            <el-input-number v-model="form.adjustQuantity" :min="-9999" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="调整原因">
            <el-input v-model="form.reason" maxlength="120" show-word-limit />
          </el-form-item>
          <el-form-item label="幂等操作号">
            <el-input v-model="form.operationId" />
          </el-form-item>
          <el-button :icon="Box" type="danger" size="large" :loading="loading" @click="submit">提交调整</el-button>
        </el-form>
      </div>

      <div class="operation-result">
        <p class="eyebrow">Result</p>
        <h2>{{ result ? '调整成功' : '等待提交' }}</h2>
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

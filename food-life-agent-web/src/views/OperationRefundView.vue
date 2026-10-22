<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { Refresh, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { operationConfirmRefund, reconcileOperationPayments } from '../api/trade'
import type { PaymentReconcileResponse, RefundOrderResponse } from '../types/order'

const loading = ref(false)
const reconciling = ref(false)
const refund = ref<RefundOrderResponse>()
const reconcile = ref<PaymentReconcileResponse>()
let active = true
onBeforeUnmount(() => { active = false })

const form = reactive({
  orderId: undefined as number | undefined,
  userId: undefined as number | undefined,
  refundReason: '运营确认退款',
})

async function confirmRefund() {
  if (loading.value) return
  if (!Number.isSafeInteger(form.orderId) || !form.orderId || form.orderId < 1 || !form.refundReason.trim()) {
    ElMessage.warning('请填写订单 ID')
    return
  }
  loading.value = true
  try {
    const result = await operationConfirmRefund(form.orderId, form.userId, form.refundReason)
    if (!active) return
    refund.value = result
    ElMessage.success('退款确认完成')
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    loading.value = false
  }
}

async function reconcilePayments() {
  if (reconciling.value) return
  reconciling.value = true
  try {
    const result = await reconcileOperationPayments(50)
    if (!active) return
    reconcile.value = result
    ElMessage.success('支付对账完成')
  } catch {
    // The HTTP interceptor reports request errors.
  } finally {
    reconciling.value = false
  }
}
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">运营工作台 · 售后服务</p>
        <h1>退款确认</h1>
        <p class="page-description">核对订单信息后处理退款，并跟进支付对账结果。</p>
      </div>
      <el-button :icon="Refresh" :loading="reconciling" @click="reconcilePayments">支付对账</el-button>
    </div>

    <section class="operation-workbench">
      <div class="operation-panel">
        <div class="panel-title">
          <el-icon><Wallet /></el-icon>
          <h2>退款处理</h2>
        </div>
        <el-form label-position="top">
          <el-form-item label="订单 ID">
            <el-input-number v-model="form.orderId" aria-label="订单 ID" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="用户 ID">
            <el-input-number v-model="form.userId" aria-label="用户 ID" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="退款原因">
            <el-input v-model="form.refundReason" maxlength="120" show-word-limit />
          </el-form-item>
          <el-button :icon="Wallet" type="danger" size="large" :loading="loading" @click="confirmRefund">
            确认退款
          </el-button>
        </el-form>
      </div>

      <div class="operation-result">
        <p class="eyebrow">处理结果</p>
        <h2>{{ refund ? refund.refundBehavior : '等待处理' }}</h2>
        <p v-if="!refund">填写左侧订单信息并提交后，退款结果会展示在这里。</p>
        <div v-if="refund" class="metric-grid">
          <span>订单 #{{ refund.orderId }}</span>
          <span>{{ refund.orderStatus }}</span>
          <span>{{ refund.refundOrderNo || '-' }}</span>
          <span>{{ refund.outRefundNo || '-' }}</span>
        </div>
        <div v-if="reconcile" class="metric-grid muted-grid">
          <span>支付 {{ reconcile.scannedPaymentCount }}</span>
          <span>成功 {{ reconcile.successPaymentCount }}</span>
          <span>退款 {{ reconcile.successRefundCount }}</span>
          <span>异常 {{ reconcile.inconsistentPayOrderNos?.length || 0 }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Refresh, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { operationConfirmRefund, reconcileOperationPayments } from '../api/trade'
import type { PaymentReconcileResponse, RefundOrderResponse } from '../types/order'

const loading = ref(false)
const reconciling = ref(false)
const refund = ref<RefundOrderResponse>()
const reconcile = ref<PaymentReconcileResponse>()

const form = reactive({
  orderId: undefined as number | undefined,
  userId: undefined as number | undefined,
  refundReason: '运营确认退款',
})

async function confirmRefund() {
  if (!form.orderId) {
    ElMessage.warning('请填写订单 ID')
    return
  }
  loading.value = true
  try {
    refund.value = await operationConfirmRefund(form.orderId, form.userId, form.refundReason)
    ElMessage.success('退款确认完成')
  } finally {
    loading.value = false
  }
}

async function reconcilePayments() {
  reconciling.value = true
  try {
    reconcile.value = await reconcileOperationPayments(50)
    ElMessage.success('支付对账完成')
  } finally {
    reconciling.value = false
  }
}
</script>

<template>
  <section class="operation-view">
    <div class="operation-hero">
      <div>
        <p class="eyebrow">Refund Operation</p>
        <h1>退款确认</h1>
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
            <el-input-number v-model="form.orderId" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="用户 ID">
            <el-input-number v-model="form.userId" :min="1" controls-position="right" />
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
        <p class="eyebrow">Result</p>
        <h2>{{ refund ? refund.refundBehavior : '等待处理' }}</h2>
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

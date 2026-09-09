import { request } from './http'
import type {
  CancelOrderResponse,
  CreateGroupBuyOrderRequest,
  CreateOrderRequest,
  CreateOrderResponse,
  CreateSeckillOrderRequest,
  OrderListResponse,
  OrderStatus,
  OperationAuditLogListResponse,
  OperationAuditQuery,
  OperationMqMessageListResponse,
  OperationMqQueueListResponse,
  OperationMqRepublishResponse,
  OperationOrderQuery,
  OperationPackageStockAdjustRequest,
  OperationPackageStockAdjustResponse,
  PaymentCallbackRequest,
  PaymentCallbackResponse,
  PaymentOrderResponse,
  PaymentPrepareRequest,
  PaymentReconcileResponse,
  RefundOrderResponse,
  SeckillActivityListResponse,
  TradeType,
  UseOrderResponse,
} from '../types/order'

export function createNormalOrder(data: CreateOrderRequest) {
  return request<CreateOrderResponse>({
    url: '/trade-api/orders/normal',
    method: 'POST',
    data,
  })
}

export function createGroupBuyOrder(data: CreateGroupBuyOrderRequest) {
  return request<CreateOrderResponse>({
    url: '/trade-api/orders/group-buy',
    method: 'POST',
    data,
  })
}

export function createSeckillOrder(data: CreateSeckillOrderRequest) {
  return request<CreateOrderResponse>({
    url: '/trade-api/orders/seckill',
    method: 'POST',
    data,
  })
}

export function queryOrders(params: {
  tradeType?: TradeType | ''
  orderStatus?: OrderStatus | ''
  pageSize?: number
  lastId?: number
}) {
  return request<OrderListResponse>({
    url: '/trade-api/orders',
    method: 'GET',
    params,
  })
}

export function querySeckillActivities(packageId?: number) {
  return request<SeckillActivityListResponse>({
    url: '/trade-api/seckill/activities',
    method: 'GET',
    params: { packageId, limit: 10 },
  })
}

export function preparePayment(
  orderId: number,
  data: PaymentPrepareRequest = {
    source: 'FOOD_LIFE_WEB',
    channel: 'LOCAL_PAY',
  },
) {
  return request<PaymentOrderResponse>({
    url: `/trade-api/pay/orders/${orderId}/prepare`,
    method: 'POST',
    data,
  })
}

export function localPaymentCallback(data: PaymentCallbackRequest) {
  return request<PaymentCallbackResponse>({
    url: '/trade-api/pay/callback/local',
    method: 'POST',
    data,
  })
}

export function cancelOrder(orderId: number) {
  return request<CancelOrderResponse>({
    url: `/trade-api/orders/${orderId}/cancel`,
    method: 'POST',
  })
}

export function applyRefund(orderId: number) {
  return request<RefundOrderResponse>({
    url: `/trade-api/orders/${orderId}/refund/apply`,
    method: 'POST',
    data: {
      source: 'FOOD_LIFE_WEB',
      channel: 'LOCAL_PAY',
      refundReason: 'web user refund',
    },
  })
}

export function useOrder(orderId: number) {
  return request<UseOrderResponse>({
    url: `/trade-api/orders/${orderId}/use/local`,
    method: 'POST',
  })
}

export function queryOperationAuditLogs(params: OperationAuditQuery) {
  return request<OperationAuditLogListResponse>({
    url: '/trade-api/operations/audit-logs',
    method: 'GET',
    params,
  })
}

export function operationAdjustPackageStock(data: OperationPackageStockAdjustRequest) {
  return request<OperationPackageStockAdjustResponse>({
    url: '/trade-api/operations/package-stock-adjustments',
    method: 'POST',
    data,
  })
}

export function operationConfirmRefund(orderId: number, userId?: number, refundReason = 'operation refund confirm') {
  return request<RefundOrderResponse>({
    url: `/trade-api/orders/${orderId}/refund/confirm`,
    method: 'POST',
    data: {
      source: 'FOOD_LIFE_OPERATION',
      channel: 'LOCAL_PAY',
      userId,
      refundReason,
    },
  })
}

export function queryOperationOrders(params: OperationOrderQuery) {
  return request<OrderListResponse>({
    url: '/trade-api/operations/orders',
    method: 'GET',
    params,
  })
}

export function queryOperationMqMessages(params: {
  messageStatus?: string
  messageType?: string
  bizId?: string
  limit?: number
}) {
  return request<OperationMqMessageListResponse>({
    url: '/trade-api/operations/mq/messages',
    method: 'GET',
    params,
  })
}

export function republishOperationMqMessage(messageId: string) {
  return request<OperationMqRepublishResponse>({
    url: `/trade-api/operations/mq/messages/${encodeURIComponent(messageId)}/republish`,
    method: 'POST',
  })
}

export function queryOperationMqQueues() {
  return request<OperationMqQueueListResponse>({
    url: '/trade-api/operations/mq/queues',
    method: 'GET',
  })
}

export function reconcileOperationPayments(limit = 50) {
  return request<PaymentReconcileResponse>({
    url: '/trade-api/operations/payments/reconcile',
    method: 'POST',
    params: { limit },
  })
}

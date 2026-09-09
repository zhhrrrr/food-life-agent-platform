export type TradeType = 'NORMAL' | 'GROUP_BUY' | 'SECKILL'
export type OrderStatus = 'WAIT_PAY' | 'PAID' | 'USED' | 'CANCELED' | 'REFUNDED'

export interface CreateOrderRequest {
  packageId: number
  quantity: number
  userCouponId?: number
}

export interface CreateGroupBuyOrderRequest {
  packageId: number
  quantity: number
  teamId?: string
}

export interface CreateSeckillOrderRequest {
  activityId: number
  quantity: number
}

export interface CreateOrderResponse {
  orderId: number
  orderNo: string
  totalAmount?: number
  discountAmount?: number
  payAmount: number
  userCouponId?: number
  orderStatus: OrderStatus
  teamId?: string
  activityId?: number
  teamStatus?: string
}

export interface OrderInfo {
  orderId: number
  orderNo: string
  userId: number
  shopId: number
  shopNameSnapshot: string
  packageId: number
  packageNameSnapshot: string
  coverImageSnapshot: string
  quantity: number
  totalAmount: number
  discountAmount: number
  payAmount: number
  userCouponId?: number
  tradeType: TradeType
  orderStatus: OrderStatus
  useTime?: string
  createTime: string
}

export interface OrderListResponse {
  orders: OrderInfo[]
  hasMore: boolean
  lastId?: number
  tradeType?: TradeType
  orderStatus?: OrderStatus
}

export interface SeckillActivity {
  activityId: number
  packageId: number
  activityName: string
  seckillPrice: number
  activityStatus: number
  validStartTime: string
  validEndTime: string
  stock: number
  userTakeLimit: number
  canBuy: boolean
}

export interface SeckillActivityListResponse {
  activities: SeckillActivity[]
}

export interface PaymentPrepareRequest {
  source: string
  channel: string
}

export interface PaymentOrderResponse {
  payOrderNo: string
  orderId: number
  orderNo: string
  userId: number
  source: string
  channel: string
  payAmount: number
  payStatus: string
  outTradeNo?: string
  payTime?: string
  createTime?: string
  updateTime?: string
}

export interface PaymentCallbackRequest {
  payOrderNo: string
  outTradeNo: string
  payAmount: number
  payTime: string
  signType: 'SHA256'
  signature: string
}

export interface PaySettlementResponse {
  source: string
  channel: string
  userId: number
  orderId: number
  orderNo: string
  orderStatus: OrderStatus
  outTradeNo: string
  outTradeTime: string
  teamId?: string
  activityId?: number
  teamStatus?: string
  targetCount?: number
  lockCount?: number
  completeCount?: number
}

export interface PaymentCallbackResponse {
  callbackBehavior: string
  paymentOrder: PaymentOrderResponse
  settlement: PaySettlementResponse
}

export interface CancelOrderResponse {
  orderId: number
  orderNo: string
  orderStatus: OrderStatus
}

export interface RefundOrderResponse {
  source: string
  channel: string
  userId: number
  orderId: number
  orderNo: string
  orderStatus: OrderStatus
  refundBehavior: string
  refundOrderNo?: string
  outRefundNo?: string
  userCouponId?: number
  couponReturned?: boolean
  couponReturnStatus?: string
  paymentRefunded?: boolean
  packageStockRolledBack?: boolean
  packageStockReleased?: boolean
  teamId?: string
  activityId?: number
  teamStatus?: string
  targetCount?: number
  lockCount?: number
  completeCount?: number
}

export interface UseOrderResponse {
  userId: number
  orderId: number
  orderNo: string
  shopId: number
  packageId: number
  tradeType: TradeType
  orderStatus: OrderStatus
  useBehavior: string
  useRecordId?: number
  useRecordNo?: string
  useTime?: string
}

export interface OperationAuditLog {
  id: number
  traceId?: string
  operatorId?: number
  operatorRole?: string
  operationType: string
  bizType: string
  bizId: string
  operationStatus: string
  requestContent?: string
  responseContent?: string
  remark?: string
  createTime: string
}

export interface OperationAuditLogListResponse {
  logs: OperationAuditLog[]
}

export interface OperationAuditQuery {
  traceId?: string
  operatorId?: number
  operationType?: string
  bizType?: string
  bizId?: string
  limit?: number
}

export interface OperationPackageStockAdjustRequest {
  packageId: number
  adjustQuantity: number
  reason: string
  operationId: string
}

export interface OperationPackageStockAdjustResponse {
  operationId: string
  operatorId: number
  packageId: number
  adjustQuantity: number
  stock: number
  sold: number
  txStatus: string
}

export interface OperationOrderQuery {
  userId?: number
  orderId?: number
  orderNo?: string
  tradeType?: TradeType | ''
  orderStatus?: OrderStatus | ''
  pageSize?: number
}

export interface OperationMqMessage {
  id: number
  messageId: string
  messageType: string
  bizType: string
  bizId: string
  messageStatus: string
  retryCount: number
  maxRetryCount: number
  nextRetryTime?: string
  content?: string
  failReason?: string
  createTime: string
  updateTime: string
}

export interface OperationMqMessageListResponse {
  messages: OperationMqMessage[]
}

export interface OperationMqRepublishResponse {
  messageId: string
  accepted: boolean
  messageStatus: string
  remark: string
}

export interface OperationMqQueue {
  queueName: string
  messageCount: number
  consumerCount: number
  backlogThreshold: number
  backlogAlarm: boolean
}

export interface OperationMqQueueListResponse {
  queues: OperationMqQueue[]
}

export interface PaymentReconcileResponse {
  scannedPaymentCount: number
  preparedPaymentCount: number
  successPaymentCount: number
  closedPaymentCount: number
  refundedPaymentCount: number
  scannedRefundCount: number
  preparedRefundCount: number
  successRefundCount: number
  failedRefundCount: number
  inconsistentPayOrderNos: string[]
}

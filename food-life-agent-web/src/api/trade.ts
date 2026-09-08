import { request } from './http'
import type {
  CancelOrderResponse,
  CreateGroupBuyOrderRequest,
  CreateOrderRequest,
  CreateOrderResponse,
  CreateSeckillOrderRequest,
  OrderListResponse,
  OrderStatus,
  PaymentCallbackRequest,
  PaymentCallbackResponse,
  PaymentOrderResponse,
  PaymentPrepareRequest,
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
    },
  })
}

export function useOrder(orderId: number) {
  return request<UseOrderResponse>({
    url: `/trade-api/orders/${orderId}/use/local`,
    method: 'POST',
  })
}

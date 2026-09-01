import { request } from './http'
import type {
  CreateGroupBuyOrderRequest,
  CreateOrderRequest,
  CreateOrderResponse,
  CreateSeckillOrderRequest,
  OrderListResponse,
  OrderStatus,
  SeckillActivityListResponse,
  TradeType,
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

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

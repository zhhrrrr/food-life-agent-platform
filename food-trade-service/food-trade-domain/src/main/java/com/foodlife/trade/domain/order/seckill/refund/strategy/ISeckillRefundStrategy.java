package com.foodlife.trade.domain.order.seckill.refund.strategy;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;

public interface ISeckillRefundStrategy {

    OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order);
}

package com.foodlife.trade.domain.order.groupbuy.refund.strategy;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;

public interface IGroupBuyRefundStrategy {

    OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order);
}

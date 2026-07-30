package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.patterns.strategy.BusinessStrategy;

public interface OrderPricingStrategy extends BusinessStrategy<OrderCreateContext, OrderPricingResult> {
}

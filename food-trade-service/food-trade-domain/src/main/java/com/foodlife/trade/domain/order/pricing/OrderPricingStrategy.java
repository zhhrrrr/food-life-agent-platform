package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;

public interface OrderPricingStrategy {

    boolean support(String tradeType);

    OrderPricingResult calculate(OrderCreateContext context);
}

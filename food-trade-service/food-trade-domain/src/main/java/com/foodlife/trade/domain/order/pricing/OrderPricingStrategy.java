package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.patterns.framework.tree.StrategyHandler;

public interface OrderPricingStrategy extends StrategyHandler<OrderCreateContext, OrderCreateContext, OrderPricingResult> {

    boolean support(String tradeType);
}

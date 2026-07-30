package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.constant.OrderPatternGroups;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import com.foodlife.patterns.strategy.BusinessStrategyRouter;
import org.springframework.stereotype.Component;

@Component
public class OrderPricingService {

    private final BusinessStrategyRouter businessStrategyRouter;

    public OrderPricingService(BusinessStrategyRouter businessStrategyRouter) {
        this.businessStrategyRouter = businessStrategyRouter;
    }

    public OrderPricingResult calculate(OrderCreateContext context) {
        return businessStrategyRouter.apply(OrderPatternGroups.PRICING, context);
    }
}

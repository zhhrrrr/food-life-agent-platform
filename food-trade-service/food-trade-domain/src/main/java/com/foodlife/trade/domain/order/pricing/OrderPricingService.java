package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderPricingService {

    private final List<OrderPricingStrategy> pricingStrategies;

    public OrderPricingService(List<OrderPricingStrategy> pricingStrategies) {
        this.pricingStrategies = pricingStrategies;
    }

    public OrderPricingResult calculate(OrderCreateContext context) {
        return pricingStrategies.stream()
                .filter(strategy -> strategy.support(context.getTradeType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("trade type not supported"))
                .calculate(context);
    }
}

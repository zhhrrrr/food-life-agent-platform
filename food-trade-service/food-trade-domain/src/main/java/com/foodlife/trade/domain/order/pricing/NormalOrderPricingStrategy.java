package com.foodlife.trade.domain.order.pricing;

import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.OrderPricingResult;
import org.springframework.stereotype.Component;

@Component
public class NormalOrderPricingStrategy implements OrderPricingStrategy {

    @Override
    public boolean support(String tradeType) {
        return TradeTypeConstants.NORMAL.equals(tradeType);
    }

    @Override
    public OrderPricingResult calculate(OrderCreateContext context) {
        Long totalAmount = context.getPackageSnapshot().getPrice() * context.getCommand().getQuantity();
        OrderPricingResult result = new OrderPricingResult();
        result.setTotalAmount(totalAmount);
        result.setPayAmount(totalAmount);
        return result;
    }
}

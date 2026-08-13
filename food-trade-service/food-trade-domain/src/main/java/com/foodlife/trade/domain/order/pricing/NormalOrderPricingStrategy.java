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
    public OrderPricingResult apply(OrderCreateContext context, OrderCreateContext dynamicContext) {
        Long totalAmount = context.getPackageSnapshot().getPrice() * context.getCommand().getQuantity();
        Long discountAmount = context.getUserCoupon() == null ? 0L : context.getUserCoupon().getDiscountAmount();
        Long payAmount = Math.max(0L, totalAmount - discountAmount);
        OrderPricingResult result = new OrderPricingResult();
        result.setTotalAmount(totalAmount);
        result.setDiscountAmount(discountAmount);
        result.setPayAmount(payAmount);
        result.setUserCouponId(context.getUserCoupon() == null ? null : context.getUserCoupon().getId());
        return result;
    }
}

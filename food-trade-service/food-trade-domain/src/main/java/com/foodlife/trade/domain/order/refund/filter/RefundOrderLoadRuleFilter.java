package com.foodlife.trade.domain.order.refund.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;

@Component
public class RefundOrderLoadRuleFilter implements ILogicHandler<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> {

    private final IOrderRepository orderRepository;

    public RefundOrderLoadRuleFilter(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderRefundBehaviorEntity apply(OrderRefundCommandEntity requestParameter,
                                           OrderRefundRuleFilterFactory.DynamicContext dynamicContext) {
        if (requestParameter == null || requestParameter.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (requestParameter.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required");
        }
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(requestParameter.getOrderId(), requestParameter.getUserId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        dynamicContext.setOrder(order);
        return next(requestParameter, dynamicContext);
    }
}

package com.foodlife.trade.domain.order.settlement.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderSettlementLoadRuleFilter implements ILogicHandler<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> {

    private final IOrderRepository orderRepository;

    public OrderSettlementLoadRuleFilter(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderSettlementRuleFilterBackEntity apply(OrderSettlementRuleCommandEntity requestParameter,
                                                    OrderSettlementRuleFilterFactory.DynamicContext dynamicContext) {
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(requestParameter.getOrderId(), requestParameter.getUserId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        dynamicContext.setOrder(order);
        return next(requestParameter, dynamicContext);
    }
}

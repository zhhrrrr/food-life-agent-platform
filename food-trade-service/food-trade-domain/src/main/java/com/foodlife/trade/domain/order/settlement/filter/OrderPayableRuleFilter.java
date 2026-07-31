package com.foodlife.trade.domain.order.settlement.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderPayableRuleFilter implements ILogicHandler<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> {

    @Override
    public OrderSettlementRuleFilterBackEntity apply(OrderSettlementRuleCommandEntity requestParameter,
                                                    OrderSettlementRuleFilterFactory.DynamicContext dynamicContext) {
        DiningOrderEntity order = dynamicContext.getOrder();
        if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not pay");
        }
        return next(requestParameter, dynamicContext);
    }
}

package com.foodlife.trade.domain.order.settlement.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class PaySuccessCommandRuleFilter implements ILogicHandler<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> {

    @Override
    public OrderSettlementRuleFilterBackEntity apply(OrderSettlementRuleCommandEntity requestParameter,
                                                    OrderSettlementRuleFilterFactory.DynamicContext dynamicContext) {
        if (requestParameter == null || requestParameter.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (requestParameter.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required");
        }
        if (requestParameter.getOutTradeTime() == null) {
            throw new IllegalArgumentException("outTradeTime required");
        }
        return next(requestParameter, dynamicContext);
    }
}

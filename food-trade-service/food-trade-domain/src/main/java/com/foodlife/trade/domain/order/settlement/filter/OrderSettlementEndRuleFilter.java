package com.foodlife.trade.domain.order.settlement.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderSettlementEndRuleFilter implements ILogicHandler<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> {

    @Override
    public OrderSettlementRuleFilterBackEntity apply(OrderSettlementRuleCommandEntity requestParameter,
                                                    OrderSettlementRuleFilterFactory.DynamicContext dynamicContext) {
        OrderSettlementRuleFilterBackEntity filterBackEntity = new OrderSettlementRuleFilterBackEntity();
        filterBackEntity.setOrder(dynamicContext.getOrder());
        return filterBackEntity;
    }
}

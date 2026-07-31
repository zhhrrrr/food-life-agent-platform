package com.foodlife.trade.domain.order.settlement.factory;

import com.foodlife.patterns.framework.link.model2.LinkArmory;
import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.settlement.filter.OrderPayableRuleFilter;
import com.foodlife.trade.domain.order.settlement.filter.OrderSettlementEndRuleFilter;
import com.foodlife.trade.domain.order.settlement.filter.OrderSettlementLoadRuleFilter;
import com.foodlife.trade.domain.order.settlement.filter.PaySuccessCommandRuleFilter;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class OrderSettlementRuleFilterFactory {

    @Bean("orderPaySettlementRuleFilter")
    public BusinessLinkedList<OrderSettlementRuleCommandEntity, DynamicContext, OrderSettlementRuleFilterBackEntity>
    orderPaySettlementRuleFilter(PaySuccessCommandRuleFilter paySuccessCommandRuleFilter,
                                 OrderSettlementLoadRuleFilter orderSettlementLoadRuleFilter,
                                 OrderPayableRuleFilter orderPayableRuleFilter,
                                 OrderSettlementEndRuleFilter orderSettlementEndRuleFilter) {
        LinkArmory<OrderSettlementRuleCommandEntity, DynamicContext, OrderSettlementRuleFilterBackEntity> linkArmory =
                new LinkArmory<>(
                        "order_pay_settlement_rule_filter",
                        paySuccessCommandRuleFilter,
                        orderSettlementLoadRuleFilter,
                        orderPayableRuleFilter,
                        orderSettlementEndRuleFilter
                );
        return linkArmory.getLogicLink();
    }

    @Data
    public static class DynamicContext {
        private DiningOrderEntity order;
    }
}

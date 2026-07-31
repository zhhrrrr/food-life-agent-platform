package com.foodlife.trade.domain.order.refund.factory;

import com.foodlife.patterns.framework.link.model2.LinkArmory;
import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.refund.filter.RefundOrderLoadRuleFilter;
import com.foodlife.trade.domain.order.refund.filter.RefundOrderRuleFilter;
import com.foodlife.trade.domain.order.refund.filter.UniqueRefundRuleFilter;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class OrderRefundRuleFilterFactory {

    @Bean("orderRefundRuleFilter")
    public BusinessLinkedList<OrderRefundCommandEntity, DynamicContext, OrderRefundBehaviorEntity>
    orderRefundRuleFilter(RefundOrderLoadRuleFilter refundOrderLoadRuleFilter,
                          UniqueRefundRuleFilter uniqueRefundRuleFilter,
                          RefundOrderRuleFilter refundOrderRuleFilter) {
        LinkArmory<OrderRefundCommandEntity, DynamicContext, OrderRefundBehaviorEntity> linkArmory =
                new LinkArmory<>(
                        "order_refund_rule_filter",
                        refundOrderLoadRuleFilter,
                        uniqueRefundRuleFilter,
                        refundOrderRuleFilter
                );
        return linkArmory.getLogicLink();
    }

    @Data
    public static class DynamicContext {
        private DiningOrderEntity order;
    }
}

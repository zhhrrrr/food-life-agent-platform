package com.foodlife.trade.domain.order.service.refund;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderRefundService {

    private final BusinessLinkedList<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> orderRefundRuleFilter;

    public OrderRefundService(@Qualifier("orderRefundRuleFilter")
                              BusinessLinkedList<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> orderRefundRuleFilter) {
        this.orderRefundRuleFilter = orderRefundRuleFilter;
    }

    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command) {
        try {
            return orderRefundRuleFilter.apply(command, new OrderRefundRuleFilterFactory.DynamicContext());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("order refund failed", e);
        }
    }
}

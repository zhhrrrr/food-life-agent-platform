package com.foodlife.trade.domain.order.refund.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class UniqueRefundRuleFilter implements ILogicHandler<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> {

    @Override
    public OrderRefundBehaviorEntity apply(OrderRefundCommandEntity requestParameter,
                                           OrderRefundRuleFilterFactory.DynamicContext dynamicContext) {
        DiningOrderEntity order = dynamicContext.getOrder();
        if (OrderStatusConstants.REFUNDED.equals(order.getOrderStatus())) {
            return buildRefundBehavior(requestParameter, order, OrderRefundBehaviorEntity.RefundBehaviorEnum.REPEAT);
        }
        return next(requestParameter, dynamicContext);
    }

    private OrderRefundBehaviorEntity buildRefundBehavior(OrderRefundCommandEntity command,
                                                          DiningOrderEntity order,
                                                          OrderRefundBehaviorEntity.RefundBehaviorEnum behaviorEnum) {
        OrderRefundBehaviorEntity behavior = new OrderRefundBehaviorEntity();
        behavior.setSource(command.getSource());
        behavior.setChannel(command.getChannel());
        behavior.setUserId(command.getUserId());
        behavior.setOrderId(order.getId());
        behavior.setOrderNo(order.getOrderNo());
        behavior.setOrderStatus(order.getOrderStatus());
        behavior.setRefundBehavior(behaviorEnum);
        behavior.setUserCouponId(order.getUserCouponId());
        behavior.setCouponReturned(false);
        return behavior;
    }
}

package com.foodlife.trade.domain.order.refund.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.groupbuy.refund.GroupBuyRefundStrategyRouter;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.normal.service.NormalPackageStockMessageService;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Component;

@Component
public class RefundOrderRuleFilter implements ILogicHandler<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> {

    private final IOrderRepository orderRepository;
    private final GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter;
    private final NormalPackageStockMessageService normalPackageStockMessageService;

    public RefundOrderRuleFilter(IOrderRepository orderRepository,
                                 GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter,
                                 NormalPackageStockMessageService normalPackageStockMessageService) {
        this.orderRepository = orderRepository;
        this.groupBuyRefundStrategyRouter = groupBuyRefundStrategyRouter;
        this.normalPackageStockMessageService = normalPackageStockMessageService;
    }

    @Override
    public OrderRefundBehaviorEntity apply(OrderRefundCommandEntity requestParameter,
                                           OrderRefundRuleFilterFactory.DynamicContext dynamicContext) {
        DiningOrderEntity order = dynamicContext.getOrder();
        if (!OrderStatusConstants.PAID.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not refund");
        }
        if (TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())) {
            return groupBuyRefundStrategyRouter.refundOrder(requestParameter, order);
        }

        boolean success = orderRepository.updateOrderStatus(order.getId(), OrderStatusConstants.PAID, OrderStatusConstants.REFUNDED);
        if (!success) {
            throw new IllegalArgumentException("order status can not refund");
        }
        if (TradeTypeConstants.NORMAL.equals(order.getTradeType())) {
            normalPackageStockMessageService.rollbackSoldAndReleaseStock(order);
        }

        OrderRefundBehaviorEntity behavior = new OrderRefundBehaviorEntity();
        behavior.setSource(requestParameter.getSource());
        behavior.setChannel(requestParameter.getChannel());
        behavior.setUserId(requestParameter.getUserId());
        behavior.setOrderId(order.getId());
        behavior.setOrderNo(order.getOrderNo());
        behavior.setOrderStatus(OrderStatusConstants.REFUNDED);
        behavior.setRefundBehavior(OrderRefundBehaviorEntity.RefundBehaviorEnum.SUCCESS);
        return behavior;
    }
}

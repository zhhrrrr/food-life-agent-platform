package com.foodlife.trade.domain.order.refund.filter;

import com.foodlife.patterns.framework.link.model2.handler.ILogicHandler;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.coupon.model.CouponReleaseResult;
import com.foodlife.trade.domain.order.coupon.service.CouponService;
import com.foodlife.trade.domain.order.groupbuy.refund.GroupBuyRefundStrategyRouter;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.refund.SeckillRefundStrategyRouter;
import org.springframework.stereotype.Component;

@Component
public class RefundOrderRuleFilter implements ILogicHandler<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> {

    private final IOrderRepository orderRepository;
    private final GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter;
    private final SeckillRefundStrategyRouter seckillRefundStrategyRouter;
    private final CouponService couponService;
    private final IPaymentOrderRepository paymentOrderRepository;
    private final IBusinessPackagePort businessPackagePort;

    public RefundOrderRuleFilter(IOrderRepository orderRepository,
                                 GroupBuyRefundStrategyRouter groupBuyRefundStrategyRouter,
                                 SeckillRefundStrategyRouter seckillRefundStrategyRouter,
                                 CouponService couponService,
                                 IPaymentOrderRepository paymentOrderRepository,
                                 IBusinessPackagePort businessPackagePort) {
        this.orderRepository = orderRepository;
        this.groupBuyRefundStrategyRouter = groupBuyRefundStrategyRouter;
        this.seckillRefundStrategyRouter = seckillRefundStrategyRouter;
        this.couponService = couponService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.businessPackagePort = businessPackagePort;
    }

    @Override
    public OrderRefundBehaviorEntity apply(OrderRefundCommandEntity requestParameter,
                                           OrderRefundRuleFilterFactory.DynamicContext dynamicContext) {
        DiningOrderEntity order = dynamicContext.getOrder();
        if (!OrderStatusConstants.PAID.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not refund");
        }
        OrderRefundBehaviorEntity behavior;
        if (TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())) {
            behavior = groupBuyRefundStrategyRouter.refundOrder(requestParameter, order);
            return finishTradeSideRefund(order, behavior, false);
        }
        if (TradeTypeConstants.SECKILL.equals(order.getTradeType())) {
            behavior = seckillRefundStrategyRouter.refundOrder(requestParameter, order);
            return finishTradeSideRefund(order, behavior, false);
        }

        boolean success = orderRepository.updateOrderStatus(order.getId(), OrderStatusConstants.PAID, OrderStatusConstants.REFUNDED);
        if (!success) {
            throw new IllegalArgumentException("order status can not refund");
        }
        behavior = buildRefundBehavior(requestParameter, order, null);
        if (TradeTypeConstants.NORMAL.equals(order.getTradeType())) {
            return finishTradeSideRefund(order, behavior, true);
        }

        return finishTradeSideRefund(order, behavior, false);
    }

    private OrderRefundBehaviorEntity finishTradeSideRefund(DiningOrderEntity order,
                                                            OrderRefundBehaviorEntity behavior,
                                                            boolean rollbackPackageStock) {
        behavior.setPaymentRefunded(markPaymentRefundedIfPresent(order));
        CouponReleaseResult couponReleaseResult = couponService.releaseCouponWithResult(order.getUserCouponId(), order.getUserId(), order.getId());
        behavior.setCouponReturned(couponReleaseResult != null
                && couponReleaseResult.getUserCouponId() != null
                && Boolean.TRUE.equals(couponReleaseResult.getReleased()));
        behavior.setCouponReturnStatus(couponReleaseResult == null ? null : couponReleaseResult.getCouponStatus());
        if (rollbackPackageStock) {
            rollbackNormalPackageStock(order, behavior);
        }
        return behavior;
    }

    private boolean markPaymentRefundedIfPresent(DiningOrderEntity order) {
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByOrderIdAndUserId(order.getId(), order.getUserId());
        if (paymentOrder == null) {
            return false;
        }
        if (PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus())) {
            return true;
        }
        if (!PaymentOrderStatusConstants.SUCCESS.equals(paymentOrder.getPayStatus())) {
            throw new IllegalArgumentException("payment order status can not refund");
        }
        return paymentOrderRepository.markPayRefunded(order.getId(), order.getUserId(), PaymentOrderStatusConstants.SUCCESS);
    }

    private void rollbackNormalPackageStock(DiningOrderEntity order, OrderRefundBehaviorEntity behavior) {
        String operationPrefix = "REFUND:" + order.getOrderNo();
        businessPackagePort.rollbackPackageSold(order.getPackageId(), order.getQuantity(), operationPrefix + ":ROLLBACK_SOLD");
        behavior.setPackageStockRolledBack(true);
        businessPackagePort.releasePackageStock(order.getPackageId(), order.getQuantity(), operationPrefix + ":RELEASE_STOCK");
        behavior.setPackageStockReleased(true);
    }

    private OrderRefundBehaviorEntity buildRefundBehavior(OrderRefundCommandEntity requestParameter,
                                                          DiningOrderEntity order,
                                                          CouponReleaseResult couponReleaseResult) {
        OrderRefundBehaviorEntity behavior = new OrderRefundBehaviorEntity();
        behavior.setSource(requestParameter.getSource());
        behavior.setChannel(requestParameter.getChannel());
        behavior.setUserId(requestParameter.getUserId());
        behavior.setOrderId(order.getId());
        behavior.setOrderNo(order.getOrderNo());
        behavior.setOrderStatus(OrderStatusConstants.REFUNDED);
        behavior.setRefundBehavior(OrderRefundBehaviorEntity.RefundBehaviorEnum.SUCCESS);
        behavior.setUserCouponId(order.getUserCouponId());
        behavior.setCouponReturned(couponReleaseResult != null
                && couponReleaseResult.getUserCouponId() != null
                && Boolean.TRUE.equals(couponReleaseResult.getReleased()));
        behavior.setCouponReturnStatus(couponReleaseResult == null ? null : couponReleaseResult.getCouponStatus());
        behavior.setPaymentRefunded(false);
        behavior.setPackageStockRolledBack(false);
        behavior.setPackageStockReleased(false);
        return behavior;
    }
}

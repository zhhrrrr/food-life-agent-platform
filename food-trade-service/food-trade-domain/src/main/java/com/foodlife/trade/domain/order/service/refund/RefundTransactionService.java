package com.foodlife.trade.domain.order.service.refund;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.RefundJournalService;
import com.foodlife.trade.domain.order.refund.factory.OrderRefundRuleFilterFactory;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public class RefundTransactionService {
    private final IOrderRepository orders;
    private final IPaymentOrderRepository payments;
    private final RefundJournalService journal;
    private final ITradeEventPublisher events;
    private final BusinessLinkedList<OrderRefundCommandEntity, OrderRefundRuleFilterFactory.DynamicContext,
            OrderRefundBehaviorEntity> rules;

    public RefundTransactionService(IOrderRepository orders, IPaymentOrderRepository payments,
            RefundJournalService journal, ITradeEventPublisher events,
            @Qualifier("orderRefundRuleFilter") BusinessLinkedList<OrderRefundCommandEntity,
                    OrderRefundRuleFilterFactory.DynamicContext, OrderRefundBehaviorEntity> rules) {
        this.orders = orders;
        this.payments = payments;
        this.journal = journal;
        this.events = events;
        this.rules = rules;
    }

    public DiningOrderEntity prepare(OrderRefundCommandEntity command) {
        if (command == null || command.getUserId() == null || command.getOrderId() == null) {
            throw new IllegalArgumentException("refund user and order required");
        }
        DiningOrderEntity order = orders.lockOrderByIdAndUserId(command.getOrderId(), command.getUserId());
        if (order == null) { throw new IllegalArgumentException("order not found"); }
        if (!TradeTypeConstants.NORMAL.equals(order.getTradeType())
                && !TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())
                && !TradeTypeConstants.SECKILL.equals(order.getTradeType())) {
            throw new IllegalArgumentException("unsupported trade type");
        }
        if (!OrderStatusConstants.PAID.equals(order.getOrderStatus())
                && !OrderStatusConstants.REFUNDED.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not refund");
        }
        PaymentOrderEntity payment = payments.findByOrderIdAndUserId(order.getId(), order.getUserId());
        if (payment != null) {
            if (!PaymentOrderStatusConstants.SUCCESS.equals(payment.getPayStatus())
                    && !PaymentOrderStatusConstants.REFUNDED.equals(payment.getPayStatus())) {
                throw new IllegalArgumentException("payment order status can not refund");
            }
            // No order/payment mutation precedes this independent commit: no suspended row lock conflict.
            journal.prepare(payment, command.getSource(), command.getRefundReason());
        }
        return order;
    }

    public OrderRefundBehaviorEntity complete(OrderRefundCommandEntity command) {
        orders.lockOrderByIdAndUserId(command.getOrderId(), command.getUserId());
        try {
            OrderRefundBehaviorEntity result = rules.apply(command, new OrderRefundRuleFilterFactory.DynamicContext());
            if (OrderRefundBehaviorEntity.RefundBehaviorEnum.SUCCESS.equals(result.getRefundBehavior())) {
                events.publish(TradeMqTopics.TRADE_ORDER_TOPIC, TradeMqTopics.ORDER_REFUND_REQUESTED,
                        String.valueOf(result.getOrderId()), result);
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("order refund compensation failed", e);
        }
    }
}

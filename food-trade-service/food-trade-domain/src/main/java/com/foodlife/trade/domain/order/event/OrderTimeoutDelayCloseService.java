package com.foodlife.trade.domain.order.event;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.CancelOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.service.OrderDomainService;
import org.springframework.stereotype.Service;

@Service
public class OrderTimeoutDelayCloseService {

    private final IOrderRepository orderRepository;
    private final IPaymentOrderRepository paymentOrderRepository;
    private final OrderDomainService orderDomainService;
    private final ITradeEventPublisher tradeEventPublisher;

    public OrderTimeoutDelayCloseService(IOrderRepository orderRepository,
                                         IPaymentOrderRepository paymentOrderRepository,
                                         OrderDomainService orderDomainService,
                                         ITradeEventPublisher tradeEventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderDomainService = orderDomainService;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public OrderTimeoutCloseResult closeTimeoutOrder(OrderTimeoutCloseMessage message) {
        validate(message);
        OrderTimeoutCloseResult result = initResult(message);
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(message.getOrderId(), message.getUserId());
        if (order == null) {
            result.setSkipReason("order not found");
            return result;
        }
        result.setBeforeOrderStatus(order.getOrderStatus());
        result.setAfterOrderStatus(order.getOrderStatus());
        if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            result.setSkipReason("order status already changed");
            return result;
        }

        closePreparedPayment(order, result);
        CancelOrderResult cancelOrderResult = orderDomainService.cancelOrder(order.getId(), order.getUserId());
        result.setOrderCanceled(true);
        result.setAfterOrderStatus(cancelOrderResult.getOrderStatus());
        tradeEventPublisher.publish(TradeMqTopics.TRADE_ORDER_TOPIC,
                TradeMqTopics.ORDER_CANCEL_TIMEOUT,
                String.valueOf(order.getId()),
                result);
        return result;
    }

    private void closePreparedPayment(DiningOrderEntity order, OrderTimeoutCloseResult result) {
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByOrderIdAndUserId(order.getId(), order.getUserId());
        if (paymentOrder == null) {
            return;
        }
        result.setPayOrderNo(paymentOrder.getPayOrderNo());
        result.setBeforePayStatus(paymentOrder.getPayStatus());
        result.setAfterPayStatus(paymentOrder.getPayStatus());
        if (!PaymentOrderStatusConstants.PREPARED.equals(paymentOrder.getPayStatus())) {
            return;
        }
        boolean closed = paymentOrderRepository.markPayClosed(paymentOrder.getPayOrderNo(), PaymentOrderStatusConstants.PREPARED);
        result.setPaymentClosed(closed);
        if (closed) {
            result.setAfterPayStatus(PaymentOrderStatusConstants.CLOSED);
            tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                    TradeMqTopics.PAYMENT_CLOSED,
                    paymentOrder.getPayOrderNo(),
                    result);
        }
    }

    private OrderTimeoutCloseResult initResult(OrderTimeoutCloseMessage message) {
        OrderTimeoutCloseResult result = new OrderTimeoutCloseResult();
        result.setOrderId(message.getOrderId());
        result.setOrderNo(message.getOrderNo());
        result.setUserId(message.getUserId());
        result.setTradeType(message.getTradeType());
        result.setOrderCanceled(false);
        result.setPaymentClosed(false);
        result.setCloseSource("RABBITMQ_DELAY");
        return result;
    }

    private void validate(OrderTimeoutCloseMessage message) {
        if (message == null || message.getOrderId() == null || message.getUserId() == null) {
            throw new IllegalArgumentException("order timeout close message invalid");
        }
    }
}

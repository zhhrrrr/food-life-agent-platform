package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackResult;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.service.settlement.OrderPaySettlementService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentOrderService {

    private final IOrderRepository orderRepository;
    private final IPaymentOrderRepository paymentOrderRepository;
    private final OrderPaySettlementService orderPaySettlementService;
    private final ITradeEventPublisher tradeEventPublisher;

    public PaymentOrderService(IOrderRepository orderRepository,
                               IPaymentOrderRepository paymentOrderRepository,
                               OrderPaySettlementService orderPaySettlementService,
                               ITradeEventPublisher tradeEventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderPaySettlementService = orderPaySettlementService;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public PaymentOrderEntity preparePayment(PaymentPrepareCommand command) {
        validatePrepareCommand(command);
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(command.getOrderId(), command.getUserId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not pay");
        }

        PaymentOrderEntity existed = paymentOrderRepository.findByOrderIdAndUserId(command.getOrderId(), command.getUserId());
        if (existed != null) {
            return existed;
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentOrderEntity paymentOrder = new PaymentOrderEntity();
        paymentOrder.setPayOrderNo(buildPayOrderNo());
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setOrderNo(order.getOrderNo());
        paymentOrder.setUserId(order.getUserId());
        paymentOrder.setSource(readOrDefault(command.getSource(), "FOOD_LIFE"));
        paymentOrder.setChannel(readOrDefault(command.getChannel(), "MOCK_PAY"));
        paymentOrder.setPayAmount(order.getPayAmount());
        paymentOrder.setPayStatus(PaymentOrderStatusConstants.PREPARED);
        paymentOrder.setCreateTime(now);
        paymentOrder.setUpdateTime(now);
        PaymentOrderEntity savedPaymentOrder = paymentOrderRepository.save(paymentOrder);
        tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                TradeMqTopics.PAYMENT_CREATED,
                savedPaymentOrder.getPayOrderNo(),
                savedPaymentOrder);
        return savedPaymentOrder;
    }

    public PaymentCallbackResult handlePaySuccessCallback(PaymentCallbackCommand command) {
        validateCallbackCommand(command);
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPayOrderNo(command.getPayOrderNo());
        if (paymentOrder == null) {
            throw new IllegalArgumentException("payment order not found");
        }
        if (!paymentOrder.getPayAmount().equals(command.getPayAmount())) {
            throw new IllegalArgumentException("pay amount mismatch");
        }

        if (PaymentOrderStatusConstants.SUCCESS.equals(paymentOrder.getPayStatus())) {
            PaymentCallbackResult result = new PaymentCallbackResult();
            result.setPaymentOrder(paymentOrder);
            result.setCallbackBehavior("repeat");
            result.setSettlement(buildRepeatedSettlement(paymentOrder));
            tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                    TradeMqTopics.PAYMENT_SUCCESS,
                    paymentOrder.getPayOrderNo(),
                    result);
            return result;
        }

        LocalDateTime payTime = command.getPayTime() == null ? LocalDateTime.now() : command.getPayTime();
        boolean marked = paymentOrderRepository.markPaySuccess(
                paymentOrder.getPayOrderNo(),
                PaymentOrderStatusConstants.PREPARED,
                command.getOutTradeNo(),
                payTime
        );
        if (!marked) {
            throw new IllegalArgumentException("payment order status can not callback");
        }

        PaymentOrderEntity successPaymentOrder = paymentOrderRepository.findByPayOrderNo(command.getPayOrderNo());
        OrderPaySettlementEntity settlement = orderPaySettlementService.settlementOrderPaySuccess(toPaySuccessEntity(successPaymentOrder));

        PaymentCallbackResult result = new PaymentCallbackResult();
        result.setPaymentOrder(successPaymentOrder);
        result.setSettlement(settlement);
        result.setCallbackBehavior("success");
        tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                TradeMqTopics.PAYMENT_SUCCESS,
                successPaymentOrder.getPayOrderNo(),
                result);
        return result;
    }

    public PaymentOrderEntity queryPaymentOrder(String payOrderNo) {
        if (payOrderNo == null || payOrderNo.trim().isEmpty()) {
            throw new IllegalArgumentException("payOrderNo required");
        }
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPayOrderNo(payOrderNo.trim());
        if (paymentOrder == null) {
            throw new IllegalArgumentException("payment order not found");
        }
        return paymentOrder;
    }

    private void validatePrepareCommand(PaymentPrepareCommand command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("orderId required");
        }
    }

    private void validateCallbackCommand(PaymentCallbackCommand command) {
        if (command == null || command.getPayOrderNo() == null || command.getPayOrderNo().trim().isEmpty()) {
            throw new IllegalArgumentException("payOrderNo required");
        }
        if (command.getOutTradeNo() == null || command.getOutTradeNo().trim().isEmpty()) {
            throw new IllegalArgumentException("outTradeNo required");
        }
        if (command.getPayAmount() == null || command.getPayAmount() <= 0) {
            throw new IllegalArgumentException("payAmount required");
        }
    }

    private OrderPaySuccessEntity toPaySuccessEntity(PaymentOrderEntity paymentOrder) {
        OrderPaySuccessEntity entity = new OrderPaySuccessEntity();
        entity.setSource(paymentOrder.getSource());
        entity.setChannel(paymentOrder.getChannel());
        entity.setUserId(paymentOrder.getUserId());
        entity.setOrderId(paymentOrder.getOrderId());
        entity.setOutTradeNo(paymentOrder.getOutTradeNo());
        entity.setOutTradeTime(paymentOrder.getPayTime());
        return entity;
    }

    private OrderPaySettlementEntity buildRepeatedSettlement(PaymentOrderEntity paymentOrder) {
        OrderPaySettlementEntity settlementEntity = new OrderPaySettlementEntity();
        settlementEntity.setSource(paymentOrder.getSource());
        settlementEntity.setChannel(paymentOrder.getChannel());
        settlementEntity.setUserId(paymentOrder.getUserId());
        settlementEntity.setOrderId(paymentOrder.getOrderId());
        settlementEntity.setOrderNo(paymentOrder.getOrderNo());
        settlementEntity.setOrderStatus(OrderStatusConstants.PAID);
        settlementEntity.setOutTradeNo(paymentOrder.getOutTradeNo());
        settlementEntity.setOutTradeTime(paymentOrder.getPayTime());
        return settlementEntity;
    }

    private String buildPayOrderNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String readOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}

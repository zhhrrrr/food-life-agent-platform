package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackResult;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.service.settlement.OrderPaySettlementService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentOrderService {

    private final IOrderRepository orderRepository;
    private final IPaymentOrderRepository paymentOrderRepository;
    private final OrderPaySettlementService orderPaySettlementService;
    private final ITradeEventPublisher tradeEventPublisher;
    private final PaymentProviderRouter paymentProviderRouter;

    public PaymentOrderService(IOrderRepository orderRepository,
                               IPaymentOrderRepository paymentOrderRepository,
                               OrderPaySettlementService orderPaySettlementService,
                               ITradeEventPublisher tradeEventPublisher,
                               PaymentProviderRouter paymentProviderRouter) {
        this.orderRepository = orderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderPaySettlementService = orderPaySettlementService;
        this.tradeEventPublisher = tradeEventPublisher;
        this.paymentProviderRouter = paymentProviderRouter;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderEntity preparePayment(PaymentPrepareCommand command) {
        validatePrepareCommand(command);
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(command.getOrderId(), command.getUserId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (!OrderStatusConstants.WAIT_PAY.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("order status can not pay");
        }

        String channel = paymentProviderRouter.normalize(readOrDefault(command.getChannel(), PaymentChannelConstants.LOCAL_PAY));
        command.setChannel(channel);
        paymentProviderRouter.route(channel).validatePrepare(command);
        PaymentOrderEntity existed = paymentOrderRepository.findByOrderIdAndUserId(command.getOrderId(), command.getUserId());
        if (existed != null) {
            if (!channel.equals(existed.getChannel())) {
                throw new IllegalArgumentException("existing payment uses another channel; create a new order");
            }
            if (!PaymentOrderStatusConstants.PREPARED.equals(existed.getPayStatus())) {
                throw new IllegalArgumentException("payment order is no longer payable");
            }
            return existed;
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentOrderEntity paymentOrder = new PaymentOrderEntity();
        paymentOrder.setPayOrderNo(buildPayOrderNo());
        paymentOrder.setOrderId(order.getId());
        paymentOrder.setOrderNo(order.getOrderNo());
        paymentOrder.setUserId(order.getUserId());
        paymentOrder.setSource(readOrDefault(command.getSource(), "FOOD_LIFE"));
        paymentOrder.setChannel(channel);
        paymentOrder.setPayAmount(order.getPayAmount());
        paymentOrder.setPayStatus(PaymentOrderStatusConstants.PREPARED);
        paymentOrder.setCreateTime(now);
        paymentOrder.setUpdateTime(now);
        PaymentOrderEntity savedPaymentOrder;
        try {
            savedPaymentOrder = paymentOrderRepository.save(paymentOrder);
        } catch (DuplicateKeyException e) {
            PaymentOrderEntity concurrentCreated = paymentOrderRepository.findByOrderIdAndUserId(command.getOrderId(), command.getUserId());
            if (concurrentCreated != null) {
                if (!channel.equals(concurrentCreated.getChannel())
                        || !PaymentOrderStatusConstants.PREPARED.equals(concurrentCreated.getPayStatus())) {
                    throw new IllegalArgumentException("concurrent payment is no longer payable with this channel");
                }
                return concurrentCreated;
            }
            throw e;
        }
        tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                TradeMqTopics.PAYMENT_CREATED,
                savedPaymentOrder.getPayOrderNo(),
                savedPaymentOrder);
        return savedPaymentOrder;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackResult handlePaySuccessCallback(PaymentCallbackCommand command) {
        validateCallbackCommand(command);
        PaymentOrderEntity paymentOrder = paymentOrderRepository.findByPayOrderNo(command.getPayOrderNo());
        if (paymentOrder == null) {
            throw new IllegalArgumentException("payment order not found");
        }
        paymentProviderRouter.route(paymentOrder.getChannel()).verifyPaySuccessCallback(command, paymentOrder);

        if (PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus())) {
            PaymentCallbackResult result = new PaymentCallbackResult();
            result.setPaymentOrder(paymentOrder);
            result.setCallbackBehavior("repeat");
            result.setSettlement(buildRepeatedSettlement(paymentOrder));
            return result;
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
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(paymentOrder.getOrderId(), paymentOrder.getUserId());
        if (order == null) { throw new IllegalStateException("payment business order missing"); }
        settlementEntity.setOrderStatus(order.getOrderStatus());
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

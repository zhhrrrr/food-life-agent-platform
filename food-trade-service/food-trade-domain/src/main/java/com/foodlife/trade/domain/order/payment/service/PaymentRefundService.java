package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundResult;
import com.foodlife.trade.domain.order.payment.model.RefundOrderEntity;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.repository.IRefundOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentRefundService {

    private final IRefundOrderRepository refundOrderRepository;
    private final IPaymentOrderRepository paymentOrderRepository;
    private final PaymentProviderRouter paymentProviderRouter;
    private final ITradeEventPublisher tradeEventPublisher;

    public PaymentRefundService(IRefundOrderRepository refundOrderRepository,
                                IPaymentOrderRepository paymentOrderRepository,
                                PaymentProviderRouter paymentProviderRouter,
                                ITradeEventPublisher tradeEventPublisher) {
        this.refundOrderRepository = refundOrderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentProviderRouter = paymentProviderRouter;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundOrderEntity refundPaidPayment(PaymentOrderEntity paymentOrder, String source, String refundReason) {
        if (paymentOrder == null) {
            return null;
        }
        RefundOrderEntity existed = refundOrderRepository.findByPayOrderNo(paymentOrder.getPayOrderNo());
        if (existed != null && RefundOrderStatusConstants.SUCCESS.equals(existed.getRefundStatus())) {
            markPaymentRefundedIfNecessary(paymentOrder);
            return existed;
        }
        if (!PaymentOrderStatusConstants.SUCCESS.equals(paymentOrder.getPayStatus())
                && !PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus())) {
            throw new IllegalArgumentException("payment order status can not refund");
        }
        if (PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus())) {
            return existed;
        }

        RefundOrderEntity refundOrder = existed == null
                ? refundOrderRepository.save(buildPreparedRefundOrder(paymentOrder, source, refundReason))
                : existed;
        PaymentRefundResult refundResult = paymentProviderRouter.route(refundOrder.getChannel())
                .refund(toRefundCommand(refundOrder), paymentOrder);
        if (!RefundOrderStatusConstants.SUCCESS.equals(refundResult.getRefundStatus())) {
            refundOrderRepository.markRefundFailed(refundOrder.getRefundOrderNo(),
                    RefundOrderStatusConstants.PREPARED,
                    refundResult.getFailReason());
            throw new IllegalArgumentException("payment refund failed");
        }

        boolean refundMarked = refundOrderRepository.markRefundSuccess(refundOrder.getRefundOrderNo(),
                RefundOrderStatusConstants.PREPARED,
                refundResult.getOutRefundNo(),
                refundResult.getRefundTime() == null ? LocalDateTime.now() : refundResult.getRefundTime());
        if (!refundMarked) {
            throw new IllegalArgumentException("refund order status can not change");
        }
        boolean paymentMarked = paymentOrderRepository.markPayRefunded(paymentOrder.getOrderId(),
                paymentOrder.getUserId(),
                PaymentOrderStatusConstants.SUCCESS);
        if (!paymentMarked) {
            throw new IllegalArgumentException("payment order status can not refund");
        }

        RefundOrderEntity successRefundOrder = refundOrderRepository.findByRefundOrderNo(refundOrder.getRefundOrderNo());
        tradeEventPublisher.publish(TradeMqTopics.PAYMENT_TOPIC,
                TradeMqTopics.PAYMENT_REFUNDED,
                successRefundOrder.getRefundOrderNo(),
                successRefundOrder);
        return successRefundOrder;
    }

    private void markPaymentRefundedIfNecessary(PaymentOrderEntity paymentOrder) {
        if (PaymentOrderStatusConstants.REFUNDED.equals(paymentOrder.getPayStatus())) {
            return;
        }
        paymentOrderRepository.markPayRefunded(paymentOrder.getOrderId(),
                paymentOrder.getUserId(),
                PaymentOrderStatusConstants.SUCCESS);
    }

    private RefundOrderEntity buildPreparedRefundOrder(PaymentOrderEntity paymentOrder, String source, String refundReason) {
        LocalDateTime now = LocalDateTime.now();
        RefundOrderEntity refundOrder = new RefundOrderEntity();
        refundOrder.setRefundOrderNo(buildRefundOrderNo());
        refundOrder.setPayOrderNo(paymentOrder.getPayOrderNo());
        refundOrder.setOrderId(paymentOrder.getOrderId());
        refundOrder.setOrderNo(paymentOrder.getOrderNo());
        refundOrder.setUserId(paymentOrder.getUserId());
        refundOrder.setSource(readOrDefault(source, paymentOrder.getSource()));
        refundOrder.setChannel(paymentOrder.getChannel());
        refundOrder.setRefundAmount(paymentOrder.getPayAmount());
        refundOrder.setRefundStatus(RefundOrderStatusConstants.PREPARED);
        refundOrder.setRefundReason(readOrDefault(refundReason, "user refund"));
        refundOrder.setOutTradeNo(paymentOrder.getOutTradeNo());
        refundOrder.setCreateTime(now);
        refundOrder.setUpdateTime(now);
        return refundOrder;
    }

    private PaymentRefundCommand toRefundCommand(RefundOrderEntity refundOrder) {
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setRefundOrderNo(refundOrder.getRefundOrderNo());
        command.setPayOrderNo(refundOrder.getPayOrderNo());
        command.setOrderId(refundOrder.getOrderId());
        command.setOrderNo(refundOrder.getOrderNo());
        command.setUserId(refundOrder.getUserId());
        command.setSource(refundOrder.getSource());
        command.setChannel(refundOrder.getChannel());
        command.setRefundAmount(refundOrder.getRefundAmount());
        command.setRefundReason(refundOrder.getRefundReason());
        command.setOutTradeNo(refundOrder.getOutTradeNo());
        return command;
    }

    private String buildRefundOrderNo() {
        return "RF" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String readOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}


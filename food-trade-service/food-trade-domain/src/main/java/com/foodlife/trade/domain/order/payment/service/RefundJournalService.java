package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.event.TradeMqTopics;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.repository.*;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Small independent transactions: provider I/O never runs inside this bean. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public class RefundJournalService {
    private final IRefundOrderRepository refunds;
    private final IPaymentOrderRepository payments;
    private final IOrderRepository orders;
    private final ITradeEventPublisher events;

    public RefundJournalService(IRefundOrderRepository refunds, IPaymentOrderRepository payments,
                                IOrderRepository orders, ITradeEventPublisher events) {
        this.refunds = refunds;
        this.payments = payments;
        this.orders = orders;
        this.events = events;
    }

    public RefundOrderEntity prepare(PaymentOrderEntity payment, String source, String reason) {
        RefundOrderEntity existing = refunds.findByPayOrderNo(payment.getPayOrderNo());
        if (existing != null) {
            validateIdentity(existing, payment);
            return existing;
        }
        if (!PaymentOrderStatusConstants.SUCCESS.equals(payment.getPayStatus())) {
            throw new IllegalArgumentException("payment order status can not refund");
        }
        LocalDateTime now = LocalDateTime.now();
        RefundOrderEntity refund = new RefundOrderEntity();
        refund.setRefundOrderNo("RF" + payment.getPayOrderNo());
        refund.setPayOrderNo(payment.getPayOrderNo());
        refund.setOrderId(payment.getOrderId());
        refund.setOrderNo(payment.getOrderNo());
        refund.setUserId(payment.getUserId());
        refund.setSource(text(source, text(payment.getSource(), "FOOD_LIFE")));
        refund.setChannel(payment.getChannel());
        refund.setRefundAmount(payment.getPayAmount());
        refund.setRefundStatus(RefundOrderStatusConstants.PREPARED);
        refund.setRefundReason(text(reason, "order refund"));
        refund.setOutTradeNo(payment.getOutTradeNo());
        refund.setBusinessCompleted(false);
        refund.setRecoveryCount(0);
        refund.setNextRetryTime(now.plusMinutes(2));
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        try {
            return refunds.save(refund);
        } catch (DuplicateKeyException e) {
            // The duplicate insert can race under REPEATABLE_READ: use a fresh transaction on retry.
            throw new IllegalArgumentException("refund request created concurrently; retry", e);
        }
    }

    public RefundOrderEntity read(String refundOrderNo) {
        return refunds.findByRefundOrderNo(refundOrderNo);
    }

    public boolean claim(RefundOrderEntity refund) {
        return refunds.markRefundProcessing(refund.getRefundOrderNo(), refund.getRefundStatus());
    }

    public boolean recoverStale(String refundOrderNo) {
        return refunds.recoverStaleProcessing(refundOrderNo, LocalDateTime.now().minusMinutes(2));
    }

    public void failed(String refundOrderNo, String reason) {
        refunds.markRefundFailed(refundOrderNo, RefundOrderStatusConstants.PROCESSING, reason);
    }

    public RefundOrderEntity succeeded(String refundOrderNo, PaymentRefundResult result) {
        if (!refunds.markRefundSuccess(refundOrderNo, RefundOrderStatusConstants.PROCESSING,
                result.getOutRefundNo(), result.getRefundTime() == null ? LocalDateTime.now() : result.getRefundTime())) {
            RefundOrderEntity current = refunds.findByRefundOrderNo(refundOrderNo);
            if (current == null || !RefundOrderStatusConstants.SUCCESS.equals(current.getRefundStatus())) {
                throw new IllegalStateException("refund result changed; reconcile the same request");
            }
        }
        return refunds.findByRefundOrderNo(refundOrderNo);
    }

    public void completePayment(RefundOrderEntity refund) {
        if (!RefundOrderStatusConstants.SUCCESS.equals(refund.getRefundStatus())) {
            throw new IllegalArgumentException("refund has not been confirmed");
        }
        PaymentOrderEntity payment = payments.findByPayOrderNo(refund.getPayOrderNo());
        validateIdentity(refund, payment);
        if (PaymentOrderStatusConstants.REFUNDED.equals(payment.getPayStatus())) { return; }
        if (!PaymentOrderStatusConstants.SUCCESS.equals(payment.getPayStatus())) {
            // Recovery of historical late-payment transactions whose receipt update rolled back.
            DiningOrderEntity order = orders.findOrderByIdAndUserId(refund.getOrderId(), refund.getUserId());
            if (!"ALIPAY_LATE_PAYMENT".equals(refund.getSource()) || order == null
                    || !OrderStatusConstants.CANCELED.equals(order.getOrderStatus())
                    || !(PaymentOrderStatusConstants.PREPARED.equals(payment.getPayStatus())
                         || PaymentOrderStatusConstants.CLOSED.equals(payment.getPayStatus()))
                    || !payments.markPaySuccess(payment.getPayOrderNo(), payment.getPayStatus(),
                            refund.getOutTradeNo(), refund.getRefundTime())) {
                throw new IllegalStateException("confirmed refund cannot repair payment state");
            }
        }
        if (!payments.markPayRefunded(refund.getOrderId(), refund.getUserId(), PaymentOrderStatusConstants.SUCCESS)) {
            throw new IllegalStateException("payment refund state changed; retry reconciliation");
        }
        events.publish(TradeMqTopics.PAYMENT_TOPIC, TradeMqTopics.PAYMENT_REFUNDED, refund.getRefundOrderNo(), refund);
    }

    public void businessCompleted(String refundOrderNo) {
        RefundOrderEntity refund = refunds.findByRefundOrderNo(refundOrderNo);
        if (refund == null || !RefundOrderStatusConstants.SUCCESS.equals(refund.getRefundStatus())) {
            throw new IllegalStateException("refund money result not confirmed");
        }
        PaymentOrderEntity payment = payments.findByPayOrderNo(refund.getPayOrderNo());
        DiningOrderEntity order = orders.findOrderByIdAndUserId(refund.getOrderId(), refund.getUserId());
        if (payment == null || !PaymentOrderStatusConstants.REFUNDED.equals(payment.getPayStatus())
                || order == null || !(OrderStatusConstants.REFUNDED.equals(order.getOrderStatus())
                    || ("ALIPAY_LATE_PAYMENT".equals(refund.getSource())
                        && OrderStatusConstants.CANCELED.equals(order.getOrderStatus())))) {
            throw new IllegalStateException("refund business compensation not committed");
        }
        refunds.markBusinessCompleted(refundOrderNo);
    }

    public boolean resetRecovery(String refundOrderNo) { return refunds.resetRecovery(refundOrderNo); }

    public void recoveryFailed(String refundOrderNo, String reason) { refunds.recordRecoveryFailure(refundOrderNo, reason); }

    public boolean claimRecovery(RefundOrderEntity refund, LocalDateTime now) {
        int attempts = refund.getRecoveryCount() == null ? 0 : refund.getRecoveryCount();
        return refunds.claimRecoveryAttempt(refund.getRefundOrderNo(), now,
                now.plusSeconds(Math.min(1800L, 60L << Math.min(attempts, 5))));
    }

    private void validateIdentity(RefundOrderEntity refund, PaymentOrderEntity payment) {
        if (payment == null || !Objects.equals(refund.getOrderId(), payment.getOrderId())
                || !Objects.equals(refund.getUserId(), payment.getUserId())
                || !Objects.equals(refund.getChannel(), payment.getChannel())
                || !Objects.equals(refund.getRefundAmount(), payment.getPayAmount())) {
            throw new IllegalArgumentException("refund and payment identity mismatch");
        }
    }

    private String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

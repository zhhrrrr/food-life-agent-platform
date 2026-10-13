package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.repository.*;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.service.refund.OrderRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class RefundRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(RefundRecoveryService.class);
    private final IRefundOrderRepository refunds;
    private final IPaymentOrderRepository payments;
    private final IOrderRepository orders;
    private final PaymentRefundService paymentRefunds;
    private final OrderRefundService orderRefunds;
    private final RefundJournalService journal;

    public RefundRecoveryService(IRefundOrderRepository refunds, IPaymentOrderRepository payments,
            IOrderRepository orders, PaymentRefundService paymentRefunds, OrderRefundService orderRefunds,
            RefundJournalService journal) {
        this.refunds = refunds;
        this.payments = payments;
        this.orders = orders;
        this.paymentRefunds = paymentRefunds;
        this.orderRefunds = orderRefunds;
        this.journal = journal;
    }

    public int recover(Integer limit) {
        LocalDateTime now = LocalDateTime.now();
        int completed = 0;
        for (RefundOrderEntity refund : refunds.listRecoverableRefundOrders(now, limit)) {
            if (!journal.claimRecovery(refund, now)) { continue; }
            if (recoverOne(refund)) { completed++; }
        }
        return completed;
    }

    public boolean retry(String refundOrderNo) {
        RefundOrderEntity refund = journal.read(refundOrderNo);
        if (refund == null) { throw new IllegalArgumentException("refund order not found"); }
        if (Boolean.TRUE.equals(refund.getBusinessCompleted())) { return true; }
        if (!journal.resetRecovery(refundOrderNo)) { return false; }
        refund = journal.read(refundOrderNo);
        return journal.claimRecovery(refund, LocalDateTime.now()) && recoverOne(refund);
    }

    private boolean recoverOne(RefundOrderEntity refund) {
            try {
                DiningOrderEntity order = orders.findOrderByIdAndUserId(refund.getOrderId(), refund.getUserId());
                if (order == null) { throw new IllegalStateException("refund business order missing"); }
                if (OrderStatusConstants.CANCELED.equals(order.getOrderStatus())
                        && "ALIPAY_LATE_PAYMENT".equals(refund.getSource())) {
                    PaymentOrderEntity payment = payments.findByPayOrderNo(refund.getPayOrderNo());
                    if (payment == null) { throw new IllegalStateException("refund payment order missing"); }
                    paymentRefunds.refundPaidPayment(payment,
                            refund.getSource(), refund.getRefundReason());
                    journal.businessCompleted(refund.getRefundOrderNo());
                } else {
                    OrderRefundCommandEntity command = new OrderRefundCommandEntity();
                    command.setOrderId(refund.getOrderId());
                    command.setUserId(refund.getUserId());
                    command.setSource(refund.getSource());
                    command.setChannel(refund.getChannel());
                    command.setRefundReason(refund.getRefundReason());
                    orderRefunds.refundOrder(command);
                }
                return true;
            } catch (Exception e) {
                try { journal.recoveryFailed(refund.getRefundOrderNo(), e.getMessage()); }
                catch (Exception recordFailure) { e.addSuppressed(recordFailure); }
                LOG.error("Refund recovery incomplete, refundOrderNo={}, automaticAttempt={}, reason={}",
                        refund.getRefundOrderNo(), (refund.getRecoveryCount() == null ? 0 : refund.getRecoveryCount()) + 1,
                        e.getMessage());
                return false;
            }
    }
}

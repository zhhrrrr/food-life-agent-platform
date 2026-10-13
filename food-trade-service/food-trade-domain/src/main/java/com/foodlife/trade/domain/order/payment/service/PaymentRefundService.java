package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.provider.*;
import com.foodlife.trade.domain.order.payment.repository.IRefundOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRefundService {
    private final IRefundOrderRepository refunds;
    private final PaymentProviderRouter providers;
    private final RefundJournalService journal;

    public PaymentRefundService(IRefundOrderRepository refunds, PaymentProviderRouter providers,
                                RefundJournalService journal) {
        this.refunds = refunds;
        this.providers = providers;
        this.journal = journal;
    }

    /** The stable request and every result are committed separately from business compensation. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundOrderEntity refundPaidPayment(PaymentOrderEntity payment, String source, String reason) {
        if (payment == null) { return null; }
        RefundOrderEntity refund = journal.prepare(payment, source, reason);
        if (RefundOrderStatusConstants.PROCESSING.equals(refund.getRefundStatus())) {
            if (!journal.recoverStale(refund.getRefundOrderNo())) {
                throw new IllegalArgumentException("refund order processing; retry later");
            }
            refund = journal.read(refund.getRefundOrderNo());
        }
        if (!RefundOrderStatusConstants.SUCCESS.equals(refund.getRefundStatus())) {
            if (!(RefundOrderStatusConstants.PREPARED.equals(refund.getRefundStatus())
                    || RefundOrderStatusConstants.FAILED.equals(refund.getRefundStatus()))) {
                throw new IllegalArgumentException("refund order status can not process");
            }
            if (!journal.claim(refund)) {
                throw new IllegalArgumentException("refund order processing; retry later");
            }
            PaymentRefundCommand command = toRefundCommand(refund);
            try {
                IPaymentProvider provider = providers.route(refund.getChannel());
                // A timeout or failed local commit does not prove the provider failed to refund.
                PaymentRefundResult result = provider.queryRefund(command, payment);
                if (result == null) { result = provider.refund(command, payment); }
                if (result == null || !RefundOrderStatusConstants.SUCCESS.equals(result.getRefundStatus())
                        || !refund.getRefundOrderNo().equals(result.getRefundOrderNo())) {
                    throw new IllegalStateException(result == null ? "empty refund result"
                            : (result.getFailReason() == null ? "refund has not succeeded" : result.getFailReason()));
                }
                refund = journal.succeeded(refund.getRefundOrderNo(), result);
            } catch (RuntimeException e) {
                try {
                    journal.failed(refund.getRefundOrderNo(), e.getMessage());
                } catch (RuntimeException journalFailure) {
                    e.addSuppressed(journalFailure);
                    // The committed PROCESSING intent remains available for timeout reconciliation.
                }
                throw e;
            }
        }
        journal.completePayment(refund);
        return refund;
    }

    /** Used only by the local business transaction, which must never call the provider. */
    public RefundOrderEntity requireConfirmedRefund(PaymentOrderEntity payment) {
        RefundOrderEntity refund = refunds.findByPayOrderNo(payment.getPayOrderNo());
        if (refund == null || !RefundOrderStatusConstants.SUCCESS.equals(refund.getRefundStatus())) {
            throw new IllegalArgumentException("payment refund has not been confirmed");
        }
        return refund;
    }

    public void completeCanceledCompensation(RefundOrderEntity refund) {
        journal.businessCompleted(refund.getRefundOrderNo());
    }

    private PaymentRefundCommand toRefundCommand(RefundOrderEntity refund) {
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setRefundOrderNo(refund.getRefundOrderNo());
        command.setPayOrderNo(refund.getPayOrderNo());
        command.setOrderId(refund.getOrderId());
        command.setOrderNo(refund.getOrderNo());
        command.setUserId(refund.getUserId());
        command.setSource(refund.getSource());
        command.setChannel(refund.getChannel());
        command.setRefundAmount(refund.getRefundAmount());
        command.setRefundReason(refund.getRefundReason());
        command.setOutTradeNo(refund.getOutTradeNo());
        return command;
    }
}

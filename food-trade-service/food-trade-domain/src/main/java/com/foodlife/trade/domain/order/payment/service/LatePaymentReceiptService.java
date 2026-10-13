package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LatePaymentReceiptService {
    private final IPaymentOrderRepository payments;
    private final IOrderRepository orders;
    private final RefundJournalService journal;

    public LatePaymentReceiptService(IPaymentOrderRepository payments, IOrderRepository orders,
                                     RefundJournalService journal) {
        this.payments = payments;
        this.orders = orders;
        this.journal = journal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PaymentOrderEntity acceptVerifiedReceipt(PaymentOrderEntity original, PaymentCallbackCommand verified) {
        DiningOrderEntity order = orders.lockOrderByIdAndUserId(original.getOrderId(), original.getUserId());
        if (order == null || !OrderStatusConstants.CANCELED.equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("late payment business state changed; retry notification");
        }
        PaymentOrderEntity payment = payments.findByPayOrderNo(original.getPayOrderNo());
        if (PaymentOrderStatusConstants.REFUNDED.equals(payment.getPayStatus())) { return payment; }
        if (!PaymentOrderStatusConstants.SUCCESS.equals(payment.getPayStatus())
                && !payments.markPaySuccess(payment.getPayOrderNo(), payment.getPayStatus(),
                        verified.getOutTradeNo(), verified.getPayTime())) {
            throw new IllegalArgumentException("late payment state changed; retry notification");
        }
        payment = payments.findByPayOrderNo(payment.getPayOrderNo());
        // Even if the receipt commit fails, the verified intent survives and can be reconciled.
        journal.prepare(payment, "ALIPAY_LATE_PAYMENT", "订单取消后到账，自动原路退款");
        return payment;
    }
}

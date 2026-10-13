package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class ExternalPaymentService {
    private final PaymentOrderService payments;
    private final PaymentRefundService refunds;
    private final PaymentProviderRouter providers;
    private final IOrderRepository orderRepository;
    private final LatePaymentReceiptService lateReceipts;

    public ExternalPaymentService(PaymentOrderService payments, PaymentRefundService refunds,
                                  PaymentProviderRouter providers,
                                  IOrderRepository orderRepository, LatePaymentReceiptService lateReceipts) {
        this.payments = payments;
        this.refunds = refunds;
        this.providers = providers;
        this.orderRepository = orderRepository;
        this.lateReceipts = lateReceipts;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void acceptPaidPayment(PaymentCallbackCommand command) {
        PaymentOrderEntity payment = payments.queryPaymentOrder(command.getPayOrderNo());
        providers.route(payment.getChannel()).verifyPaySuccessCallback(command, payment);
        DiningOrderEntity order = orderRepository.findOrderByIdAndUserId(payment.getOrderId(), payment.getUserId());
        if (order == null) { throw new IllegalArgumentException("order not found"); }
        if (OrderStatusConstants.CANCELED.equals(order.getOrderStatus())) {
            // A page-pay URL may be opened after a previously non-existent trade was closed.
            // Refund that late payment without restoring the canceled order or its inventory.
            if (PaymentOrderStatusConstants.REFUNDED.equals(payment.getPayStatus())) { return; }
            payment = lateReceipts.acceptVerifiedReceipt(payment, command);
            com.foodlife.trade.domain.order.payment.model.RefundOrderEntity refund = refunds.refundPaidPayment(payment,
                    "ALIPAY_LATE_PAYMENT", "订单取消后到账，自动原路退款");
            refunds.completeCanceledCompensation(refund);
            return;
        }
        payments.handlePaySuccessCallback(command);
    }

    public PaymentOrderEntity syncPayment(String payOrderNo, Long userId) {
        PaymentOrderEntity payment = payments.queryPaymentOrder(payOrderNo);
        if (userId == null || !userId.equals(payment.getUserId())) {
            throw new IllegalArgumentException("payment order not found");
        }
        // invoke the transactional service proxy from the caller instead of self invocation
        return payment;
    }

    public PaymentCallbackCommand queryPaidPayment(PaymentOrderEntity payment) {
        if (!PaymentChannelConstants.ALIPAY_SANDBOX.equals(payment.getChannel())) { return null; }
        return providers.route(payment.getChannel()).queryPaidPayment(payment);
    }
}

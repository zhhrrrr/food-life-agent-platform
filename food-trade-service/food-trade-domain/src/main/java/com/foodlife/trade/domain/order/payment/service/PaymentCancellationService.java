package com.foodlife.trade.domain.order.payment.service;

import com.foodlife.trade.domain.order.payment.constant.PaymentOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentCancellationService {
    private final IPaymentOrderRepository repository;
    private final PaymentProviderRouter providers;
    public PaymentCancellationService(IPaymentOrderRepository repository, PaymentProviderRouter providers) {
        this.repository = repository;
        this.providers = providers;
    }

    public void closeBeforeCancellation(Long orderId, Long userId) {
        PaymentOrderEntity payment = repository.findByOrderIdAndUserId(orderId, userId);
        if (payment == null || PaymentOrderStatusConstants.CLOSED.equals(payment.getPayStatus())) { return; }
        if (!PaymentOrderStatusConstants.PREPARED.equals(payment.getPayStatus())) {
            throw new IllegalArgumentException("payment already completed; refresh order before canceling");
        }
        providers.route(payment.getChannel()).closePayment(payment);
        if (!repository.markPayClosed(payment.getPayOrderNo(), PaymentOrderStatusConstants.PREPARED)) {
            throw new IllegalArgumentException("payment changed while canceling; refresh order");
        }
    }
}

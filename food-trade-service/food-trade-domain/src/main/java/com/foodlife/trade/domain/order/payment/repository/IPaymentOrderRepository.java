package com.foodlife.trade.domain.order.payment.repository;

import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;

import java.time.LocalDateTime;

public interface IPaymentOrderRepository {

    PaymentOrderEntity save(PaymentOrderEntity paymentOrder);

    PaymentOrderEntity findByOrderIdAndUserId(Long orderId, Long userId);

    PaymentOrderEntity findByPayOrderNo(String payOrderNo);

    boolean markPaySuccess(String payOrderNo, String fromStatus, String outTradeNo, LocalDateTime payTime);

    java.util.List<PaymentOrderEntity> listTimeoutPreparedPaymentOrders(LocalDateTime timeoutBefore, Integer limit);

    boolean markPayClosed(String payOrderNo, String fromStatus);
}

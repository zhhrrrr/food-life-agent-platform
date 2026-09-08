package com.foodlife.trade.domain.order.payment.provider;

import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;

public interface IPaymentProvider {

    String channel();

    void validatePrepare(PaymentPrepareCommand command);

    void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity paymentOrder);
}

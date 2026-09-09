package com.foodlife.trade.domain.order.payment.provider;

import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundResult;

public interface IPaymentProvider {

    String channel();

    void validatePrepare(PaymentPrepareCommand command);

    void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity paymentOrder);

    PaymentRefundResult refund(PaymentRefundCommand command, PaymentOrderEntity paymentOrder);
}

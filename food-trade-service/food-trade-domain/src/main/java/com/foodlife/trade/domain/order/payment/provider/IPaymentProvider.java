package com.foodlife.trade.domain.order.payment.provider;

import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundResult;

public interface IPaymentProvider {

    String channel();

    void validatePrepare(PaymentPrepareCommand command);

    default String buildPaymentUrl(PaymentOrderEntity paymentOrder) {
        return null;
    }

    default String buildPaymentForm(PaymentOrderEntity paymentOrder) {
        return null;
    }

    /** Returns a verified paid transaction, or null while unpaid/not created. */
    default PaymentCallbackCommand queryPaidPayment(PaymentOrderEntity paymentOrder) {
        return null;
    }

    /** Must fail if the provider cannot safely close the transaction. */
    default void closePayment(PaymentOrderEntity paymentOrder) {
    }

    void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity paymentOrder);

    default void verifyNotification(java.util.Map<String, String> parameters) {
        throw new IllegalArgumentException("provider notifications unsupported");
    }

    PaymentRefundResult refund(PaymentRefundCommand command, PaymentOrderEntity paymentOrder);

    /** Confirm the same refund request before retrying it; null means no confirmed refund. */
    default PaymentRefundResult queryRefund(PaymentRefundCommand command, PaymentOrderEntity paymentOrder) {
        return null;
    }
}

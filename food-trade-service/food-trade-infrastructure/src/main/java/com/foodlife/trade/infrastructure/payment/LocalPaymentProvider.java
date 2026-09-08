package com.foodlife.trade.infrastructure.payment;

import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.provider.IPaymentProvider;
import org.springframework.stereotype.Component;

@Component
public class LocalPaymentProvider implements IPaymentProvider {

    @Override
    public String channel() {
        return PaymentChannelConstants.LOCAL_PAY;
    }

    @Override
    public void validatePrepare(PaymentPrepareCommand command) {
        if (command == null || command.getOrderId() == null || command.getUserId() == null) {
            throw new IllegalArgumentException("payment prepare command invalid");
        }
    }

    @Override
    public void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity paymentOrder) {
        if (paymentOrder == null) {
            throw new IllegalArgumentException("payment order not found");
        }
        if (!paymentOrder.getPayAmount().equals(command.getPayAmount())) {
            throw new IllegalArgumentException("pay amount mismatch");
        }
    }
}

package com.foodlife.trade.infrastructure.payment;

import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPaymentProviderTest {

    private static final String LOCAL_PAYMENT_SECRET = "local-payment-secret";

    private final LocalPaymentProvider localPaymentProvider = new LocalPaymentProvider(LOCAL_PAYMENT_SECRET);

    @Test
    void routeLocalPaymentProviderIgnoringCaseAndWhitespace() {
        PaymentProviderRouter router = new PaymentProviderRouter(Collections.singletonList(localPaymentProvider));

        assertThat(router.route(" local_pay ").channel()).isEqualTo(PaymentChannelConstants.LOCAL_PAY);
    }

    @Test
    void rejectUnsupportedPaymentChannel() {
        PaymentProviderRouter router = new PaymentProviderRouter(Collections.singletonList(localPaymentProvider));

        assertThatThrownBy(() -> router.route("WECHAT_PAY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment channel not supported");
    }

    @Test
    void rejectInvalidLocalPrepareCommand() {
        assertThatThrownBy(() -> localPaymentProvider.validatePrepare(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment prepare command invalid");
    }

    @Test
    void rejectCallbackWhenAmountMismatch() {
        PaymentOrderEntity paymentOrder = new PaymentOrderEntity();
        paymentOrder.setPayAmount(16800L);

        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setPayAmount(100L);

        assertThatThrownBy(() -> localPaymentProvider.verifyPaySuccessCallback(command, paymentOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pay amount mismatch");
    }

    @Test
    void rejectCallbackWhenSignatureInvalid() {
        PaymentOrderEntity paymentOrder = new PaymentOrderEntity();
        paymentOrder.setPayAmount(16800L);

        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setPayOrderNo("PAY202609090001");
        command.setOutTradeNo("OUT202609090001");
        command.setPayAmount(16800L);
        command.setSignType("SHA256");
        command.setSignature("bad-signature");

        assertThatThrownBy(() -> localPaymentProvider.verifyPaySuccessCallback(command, paymentOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment callback signature invalid");
    }

    @Test
    void acceptCallbackWhenSignatureValid() {
        PaymentOrderEntity paymentOrder = new PaymentOrderEntity();
        paymentOrder.setPayAmount(16800L);

        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setPayOrderNo("PAY202609090001");
        command.setOutTradeNo("OUT202609090001");
        command.setPayAmount(16800L);
        command.setSignType("SHA256");
        command.setSignature("b22f83ab7f38360be1918d9672f07b449e6c3126436042ec784ab6abe0cae856");

        localPaymentProvider.verifyPaySuccessCallback(command, paymentOrder);
    }
}

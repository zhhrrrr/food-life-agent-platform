package com.foodlife.trade.infrastructure.payment;

import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPaymentProviderTest {

    private final LocalPaymentProvider localPaymentProvider = new LocalPaymentProvider();

    @Test
    void routeLocalPaymentProviderIgnoringCaseAndWhitespace() {
        PaymentProviderRouter router = new PaymentProviderRouter(List.of(localPaymentProvider));

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
}

package com.foodlife.trade.trigger.http;

import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.provider.IPaymentProvider;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.payment.service.ExternalPaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AlipayNotificationTimeTest {
    @Test
    void providerTimestampIsConvertedToTheDomainLocalClock() {
        PaymentCallbackCommand command = receive("2026-10-22 15:00:00");
        assertThat(command.getPayTime().atZone(ZoneId.systemDefault()).toInstant())
                .isEqualTo(Instant.parse("2026-10-22T07:00:00Z"));
    }

    @Test
    void missingProviderTimestampUsesTheDomainLocalClock() {
        Instant before = Instant.now();
        PaymentCallbackCommand command = receive(null);
        Instant paidAt = command.getPayTime().atZone(ZoneId.systemDefault()).toInstant();
        assertThat(paidAt).isAfterOrEqualTo(before);
        assertThat(Math.abs(Duration.between(paidAt, Instant.now()).toSeconds())).isLessThanOrEqualTo(3);
    }

    private PaymentCallbackCommand receive(String time) {
        PaymentProviderRouter providers = mock(PaymentProviderRouter.class);
        ExternalPaymentService payments = mock(ExternalPaymentService.class);
        when(providers.route("ALIPAY_SANDBOX")).thenReturn(mock(IPaymentProvider.class));
        Map<String, String> parameters = new HashMap<>(Map.of(
                "trade_status", "TRADE_SUCCESS", "out_trade_no", "PAY_TEST",
                "trade_no", "ALIPAY_TEST", "total_amount", "168.00"));
        if (time != null) parameters.put("gmt_payment", time);
        assertThat(new AlipayNotificationController(providers, payments).notifyPayment(parameters)).isEqualTo("success");
        ArgumentCaptor<PaymentCallbackCommand> captured = ArgumentCaptor.forClass(PaymentCallbackCommand.class);
        verify(payments).acceptPaidPayment(captured.capture());
        return captured.getValue();
    }
}

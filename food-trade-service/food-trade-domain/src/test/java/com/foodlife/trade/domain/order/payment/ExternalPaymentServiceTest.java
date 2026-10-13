package com.foodlife.trade.domain.order.payment;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.provider.*;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.*;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class ExternalPaymentServiceTest {
    private final PaymentOrderService payments = mock(PaymentOrderService.class);
    private final PaymentRefundService refunds = mock(PaymentRefundService.class);
    private final IPaymentProvider provider = mock(IPaymentProvider.class);
    private final IPaymentOrderRepository repository = mock(IPaymentOrderRepository.class);
    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final LatePaymentReceiptService receipts = mock(LatePaymentReceiptService.class);

    private PaymentProviderRouter router() {
        when(provider.channel()).thenReturn("ALIPAY_SANDBOX");
        return new PaymentProviderRouter(List.of(provider));
    }

    private PaymentOrderEntity payment() {
        PaymentOrderEntity payment = new PaymentOrderEntity();
        payment.setPayOrderNo("PAY_TEST");
        payment.setOrderId(1L);
        payment.setUserId(2L);
        payment.setChannel("ALIPAY_SANDBOX");
        payment.setPayStatus("CLOSED");
        return payment;
    }

    @Test void rejectQueryOfAnotherUsersPayment() {
        when(payments.queryPaymentOrder("PAY_TEST")).thenReturn(payment());
        ExternalPaymentService service = new ExternalPaymentService(payments, refunds, router(), orders, receipts);
        assertThatThrownBy(() -> service.syncPayment("PAY_TEST", 3L)).hasMessage("payment order not found");
        verifyNoInteractions(refunds);
    }

    @Test void latePaymentIsRefundedWithoutRestoringCanceledOrder() {
        PaymentOrderEntity payment = payment();
        when(payments.queryPaymentOrder("PAY_TEST")).thenReturn(payment);
        DiningOrderEntity order = new DiningOrderEntity();
        order.setOrderStatus("CANCELED");
        when(orders.findOrderByIdAndUserId(1L, 2L)).thenReturn(order);
        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setPayOrderNo("PAY_TEST");
        command.setOutTradeNo("ALIPAY_TEST");
        when(receipts.acceptVerifiedReceipt(payment, command)).thenReturn(payment);
        ExternalPaymentService service = new ExternalPaymentService(payments, refunds, router(), orders, receipts);
        service.acceptPaidPayment(command);
        verify(refunds).refundPaidPayment(eq(payment), eq("ALIPAY_LATE_PAYMENT"), anyString());
        verify(payments, never()).handlePaySuccessCallback(any());
        verify(orders, never()).updateOrderStatus(any(), any(), any());
    }

    @Test void failedProviderCloseDoesNotCloseLocalPayment() {
        PaymentOrderEntity payment = payment();
        payment.setPayStatus("PREPARED");
        when(repository.findByOrderIdAndUserId(1L, 2L)).thenReturn(payment);
        PaymentCancellationService service = new PaymentCancellationService(repository, router());
        doThrow(new IllegalStateException("provider offline")).when(provider).closePayment(payment);
        assertThatThrownBy(() -> service.closeBeforeCancellation(1L, 2L)).hasMessage("provider offline");
        verify(repository, never()).markPayClosed(anyString(), anyString());
    }
}

package com.foodlife.trade.domain.order.payment;

import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.*;
import com.foodlife.trade.domain.order.service.refund.*;
import com.foodlife.trade.domain.order.seckill.repository.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OrderRefundFlowTest {
    private final RefundTransactionService transactions = mock(RefundTransactionService.class);
    private final IPaymentOrderRepository payments = mock(IPaymentOrderRepository.class);
    private final PaymentRefundService refunds = mock(PaymentRefundService.class);
    private final RefundJournalService journal = mock(RefundJournalService.class);
    private final ISeckillRepository seckill = mock(ISeckillRepository.class);
    private final ISeckillStockRepository stock = mock(ISeckillStockRepository.class);
    private final OrderRefundService service = new OrderRefundService(transactions, payments, refunds, journal, seckill, stock);

    @Test void failedMoneyRefundDoesNotCompensateBusinessOrReleaseRedisStock() {
        OrderRefundCommandEntity command = command();
        DiningOrderEntity order = order();
        PaymentOrderEntity payment = new PaymentOrderEntity();
        when(transactions.prepare(command)).thenReturn(order);
        when(payments.findByOrderIdAndUserId(1L, 2L)).thenReturn(payment);
        when(refunds.refundPaidPayment(any(), any(), any())).thenThrow(new IllegalStateException("provider failed"));
        assertThatThrownBy(() -> service.refundOrder(command)).hasMessage("provider failed");
        verify(transactions, never()).complete(any());
        verifyNoInteractions(seckill, stock);
        verify(journal, never()).businessCompleted(any());
    }

    @Test void businessCommitPrecedesIdempotentRedisReleaseAndCompletionMarker() {
        OrderRefundCommandEntity command = command();
        PaymentOrderEntity payment = new PaymentOrderEntity();
        RefundOrderEntity refund = new RefundOrderEntity();
        refund.setRefundOrderNo("RF1");
        when(transactions.prepare(command)).thenReturn(order());
        when(payments.findByOrderIdAndUserId(1L, 2L)).thenReturn(payment);
        when(refunds.refundPaidPayment(any(), any(), any())).thenReturn(refund);
        when(transactions.complete(command)).thenReturn(new OrderRefundBehaviorEntity());
        when(seckill.querySeckillActivityId(any())).thenReturn(10L);
        service.refundOrder(command);
        var calls = inOrder(transactions, refunds, stock, journal);
        calls.verify(transactions).prepare(command);
        calls.verify(refunds).refundPaidPayment(any(), any(), any());
        calls.verify(transactions).complete(command);
        calls.verify(stock).releaseActivityStock(10L, 2L, "REFUND:ORDER1");
        calls.verify(journal).businessCompleted("RF1");
    }

    @Test void failedBusinessCompletionRetainsMoneyResultForRecovery() {
        OrderRefundCommandEntity command = command();
        when(transactions.prepare(command)).thenReturn(order());
        when(payments.findByOrderIdAndUserId(1L, 2L)).thenReturn(new PaymentOrderEntity());
        when(refunds.refundPaidPayment(any(), any(), any())).thenReturn(new RefundOrderEntity());
        when(transactions.complete(command)).thenThrow(new IllegalStateException("business database failed"));
        assertThatThrownBy(() -> service.refundOrder(command)).hasMessage("business database failed");
        verifyNoInteractions(stock);
        verify(journal, never()).businessCompleted(any());
    }

    private OrderRefundCommandEntity command() {
        OrderRefundCommandEntity command = new OrderRefundCommandEntity();
        command.setOrderId(1L); command.setUserId(2L); command.setSource("TEST"); command.setRefundReason("refund");
        return command;
    }
    private DiningOrderEntity order() {
        DiningOrderEntity order = new DiningOrderEntity();
        order.setId(1L); order.setUserId(2L); order.setOrderNo("ORDER1"); order.setTradeType("SECKILL");
        return order;
    }
}

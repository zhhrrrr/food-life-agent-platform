package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.model.*;
import com.foodlife.trade.domain.order.seckill.repository.*;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeckillSyncIdempotencyTest {
    private final ISeckillRepository repository = mock(ISeckillRepository.class);
    private final ISeckillStockRepository stock = mock(ISeckillStockRepository.class);
    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final IBusinessPackagePort packages = mock(IBusinessPackagePort.class);
    private final SeckillOrderService service = new SeckillOrderService(repository, stock, orders, packages,
            mock(OrderFactory.class), mock(ITradeEventPublisher.class), mock(TransactionTemplate.class));
    private SeckillOrderCommand command() {
        SeckillOrderCommand command = new SeckillOrderCommand();
        command.setUserId(2L); command.setActivityId(10L); command.setQuantity(1);
        command.setExpectedUnitPrice(100L); command.setRequestId("sync-key");
        return command;
    }
    private DiningOrderEntity existing() {
        DiningOrderEntity order = new DiningOrderEntity();
        order.setId(1L); order.setOrderNo("ORDER1"); order.setOrderStatus("PAID"); order.setTradeType("SECKILL");
        order.setPayAmount(100L);
        order.setRequestFingerprint(TradeRequestIdentity.fingerprint("SECKILL", 10L, 1, 100L));
        when(orders.findOrderByRequestIdAndUserId("sync-key", 2L)).thenReturn(order);
        return order;
    }
    @Test void replayReturnsOriginalOrderBeforeCheckingCurrentActivityOrUserLimit() {
        existing();
        SeckillOrderResult replay = service.createSeckillOrder(command());
        assertThat(replay.getOrderId()).isEqualTo(1L);
        assertThat(replay.getOrderStatus()).isEqualTo("PAID");
        verifyNoInteractions(repository, stock, packages);
    }
    @Test void changedPriceCannotReuseAnExistingKey() {
        existing();
        SeckillOrderCommand changed = command(); changed.setExpectedUnitPrice(200L);
        assertThatThrownBy(() -> service.createSeckillOrder(changed)).hasMessageContaining("different seckill parameters");
        verifyNoInteractions(repository, stock, packages);
    }
    @Test void pendingAsyncRequestCannotReserveStockAgainThroughSyncApi() {
        when(repository.querySeckillOrderRequest(TradeRequestIdentity.seckillRequestNo(2L, "sync-key")))
                .thenReturn(new SeckillOrderRequestEntity());
        assertThatThrownBy(() -> service.createSeckillOrder(command())).hasMessageContaining("asynchronous seckill");
        verifyNoInteractions(stock, packages);
    }
    @Test void syncOrderKeyCannotCreateAnotherAsyncRequest() {
        existing();
        assertThatThrownBy(() -> service.createSeckillOrderRequest(command())).hasMessageContaining("another order");
        verifyNoInteractions(stock, packages);
    }
}

package com.foodlife.trade.domain.order.service;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.factory.OrderFactory;
import com.foodlife.trade.domain.order.groupbuy.model.*;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyLockOrderService;
import com.foodlife.trade.domain.order.seckill.model.*;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasePriceGuardTest {
    @Test void changedGroupPriceOrActivityCannotLockInventory() throws Exception {
        IGroupBuyRepository groups = mock(IGroupBuyRepository.class);
        OrderFactory factory = mock(OrderFactory.class);
        @SuppressWarnings("unchecked")
        BusinessLinkedList<GroupBuyLockContext, GroupBuyLockContext, Void> chain = mock(BusinessLinkedList.class);
        GroupBuyActivityEntity activity = new GroupBuyActivityEntity(); activity.setId(3L); activity.setGroupPrice(12000L);
        doAnswer(invocation -> { ((GroupBuyLockContext) invocation.getArgument(0)).setActivity(activity); return null; }).when(chain).apply(any(), any());
        GroupBuyLockOrderService service = new GroupBuyLockOrderService(groups, factory, chain);
        GroupBuyLockOrderCommand command = new GroupBuyLockOrderCommand(); command.setExpectedUnitPrice(11000L); command.setExpectedActivityId(3L);
        assertThrows(IllegalArgumentException.class, () -> service.lockOrder(command));
        command.setExpectedUnitPrice(12000L); command.setExpectedActivityId(4L);
        assertThrows(IllegalArgumentException.class, () -> service.lockOrder(command));
        verifyNoInteractions(groups, factory);
    }

    @Test void changedSeckillPriceFailsBeforeRedisReservation() {
        ISeckillRepository repository = mock(ISeckillRepository.class);
        ISeckillStockRepository stocks = mock(ISeckillStockRepository.class);
        SeckillActivityEntity activity = new SeckillActivityEntity(); activity.setId(7L); activity.setSeckillPrice(9900L);
        activity.setActivityStatus(1); activity.setValidStartTime(LocalDateTime.now().minusHours(1)); activity.setValidEndTime(LocalDateTime.now().plusHours(1));
        when(repository.queryActivityById(7L)).thenReturn(activity);
        SeckillOrderService service = new SeckillOrderService(repository, stocks, null, null, null, null, null);
        SeckillOrderCommand command = new SeckillOrderCommand(); command.setUserId(7L); command.setActivityId(7L); command.setQuantity(1); command.setExpectedUnitPrice(9800L);
        assertThrows(IllegalArgumentException.class, () -> service.createSeckillOrderRequest(command));
        verifyNoInteractions(stocks);
        verify(repository, never()).saveSeckillOrderRequestAndMessage(any(), any());
    }
}

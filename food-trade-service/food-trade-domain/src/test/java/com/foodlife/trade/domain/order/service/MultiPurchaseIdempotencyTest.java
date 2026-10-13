package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.groupbuy.model.*;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.groupbuy.service.GroupBuyLockOrderService;
import com.foodlife.trade.domain.order.model.*;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.model.*;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import com.foodlife.trade.domain.order.seckill.service.SeckillOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiPurchaseIdempotencyTest {
    @Mock IOrderRepository orders;
    @Mock IGroupBuyRepository groups;
    @Mock GroupBuyLockOrderService groupLock;
    @InjectMocks OrderDomainService service;

    private GroupBuyLockOrderCommand groupCommand() {
        GroupBuyLockOrderCommand c = new GroupBuyLockOrderCommand();
        c.setUserId(7L); c.setPackageId(1L); c.setQuantity(1); c.setTeamId("GBT1");
        c.setRequestId("confirmed-group"); c.setExpectedUnitPrice(12000L); c.setExpectedActivityId(3L);
        return c;
    }

    @Test void groupReplayDoesNotLockAnotherSeatOrPublishAnotherOrder() {
        GroupBuyLockOrderCommand c = groupCommand();
        DiningOrderEntity order = new DiningOrderEntity();
        order.setId(41L); order.setUserId(7L); order.setTradeType("GROUP_BUY"); order.setOrderStatus("WAIT_PAY");
        order.setRequestFingerprint(TradeRequestIdentity.fingerprint("GROUP_BUY", 1L, 1, "GBT1", 12000L, 3L));
        GroupBuyOrderListEntity member = new GroupBuyOrderListEntity(); member.setTeamId("GBT1"); member.setActivityId(3L);
        when(orders.findOrderByRequestIdAndUserId("confirmed-group", 7L)).thenReturn(order);
        when(groups.queryOrderListByOrderIdAndUserId(41L, 7L)).thenReturn(member);
        assertEquals(41L, service.createGroupBuyOrder(c).getOrderId());
        assertEquals(41L, service.createGroupBuyOrder(c).getOrderId());
        verifyNoInteractions(groupLock);
    }

    @Test void groupReplayRejectsChangedTeamOrPrice() {
        GroupBuyLockOrderCommand c = groupCommand();
        DiningOrderEntity order = new DiningOrderEntity(); order.setTradeType("GROUP_BUY");
        order.setRequestFingerprint(TradeRequestIdentity.fingerprint("GROUP_BUY", 1L, 1, "GBT1", 12000L, 3L));
        when(orders.findOrderByRequestIdAndUserId("confirmed-group", 7L)).thenReturn(order);
        c.setTeamId("GBT2");
        assertThrows(IllegalArgumentException.class, () -> service.createGroupBuyOrder(c));
        c.setTeamId("GBT1"); c.setExpectedUnitPrice(13000L);
        assertThrows(IllegalArgumentException.class, () -> service.createGroupBuyOrder(c));
        verifyNoInteractions(groupLock, groups);
    }

    @Test void seckillReplayReturnsSameRequestWithoutAnotherRedisReservation() {
        ISeckillRepository repository = mock(ISeckillRepository.class);
        ISeckillStockRepository stocks = mock(ISeckillStockRepository.class);
        SeckillOrderService seckill = new SeckillOrderService(repository, stocks, orders, null, null, null, null);
        SeckillOrderCommand c = new SeckillOrderCommand(); c.setUserId(7L); c.setActivityId(9L); c.setQuantity(1);
        c.setRequestId("confirmed-seckill"); c.setExpectedUnitPrice(9900L);
        String requestNo = TradeRequestIdentity.seckillRequestNo(7L, "confirmed-seckill");
        SeckillOrderRequestEntity request = new SeckillOrderRequestEntity(); request.setRequestNo(requestNo);
        request.setRequestStatus("INIT"); request.setRequestFingerprint(TradeRequestIdentity.fingerprint("SECKILL", 9L, 1, 9900L));
        when(repository.querySeckillOrderRequest(requestNo)).thenReturn(request);
        assertEquals(requestNo, seckill.createSeckillOrderRequest(c).getRequestNo());
        assertEquals(requestNo, seckill.createSeckillOrderRequest(c).getRequestNo());
        c.setActivityId(10L);
        assertThrows(IllegalArgumentException.class, () -> seckill.createSeckillOrderRequest(c));
        verifyNoInteractions(stocks, orders);
        verify(repository, never()).saveSeckillOrderRequestAndMessage(any(), any());
    }

    @Test void stableRequestIdentityIsScopedToOwnerAndValidatesInput() {
        assertNotEquals(TradeRequestIdentity.seckillRequestNo(7L, "request"), TradeRequestIdentity.seckillRequestNo(8L, "request"));
        assertThrows(IllegalArgumentException.class, () -> TradeRequestIdentity.seckillRequestNo(7L, "../invalid"));
        assertNotEquals(TradeRequestIdentity.fingerprint("a", "bc"), TradeRequestIdentity.fingerprint("ab", "c"));
    }
}

package com.foodlife.trade.domain.order.service;

import com.foodlife.trade.domain.order.check.OrderCreateCheckChain;
import com.foodlife.trade.domain.order.model.CreateOrderCommand;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderCreateContext;
import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.coupon.service.CouponService;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyTest {
    @Mock IOrderRepository orders;
    @Mock OrderCreateCheckChain checks;
    @Mock CouponService coupons;
    @Mock IBusinessPackagePort business;
    @InjectMocks OrderDomainService service;
    CreateOrderCommand command;
    DiningOrderEntity existing;

    @BeforeEach void setup() {
        command = new CreateOrderCommand();
        command.setUserId(7L);
        command.setPackageId(1L);
        command.setQuantity(2);
        command.setRequestId("stable-operation");
        existing = new DiningOrderEntity();
        existing.setId(42L);
        existing.setPackageId(1L);
        existing.setQuantity(2);
        existing.setPayAmount(3600L);
        existing.setOrderStatus("WAIT_PAY");
        existing.setTradeType("NORMAL");
    }

    @Test void replayReturnsOriginalOrderWithoutStockOrCouponActions() {
        when(orders.findOrderByRequestIdAndUserId("stable-operation", 7L)).thenReturn(existing);
        assertEquals(42L, service.createNormalOrder(command).getOrderId());
        assertEquals(42L, service.createNormalOrder(command).getOrderId());
        verify(orders, never()).saveOrder(any());
        verifyNoInteractions(checks);
    }

    @Test void sameKeyCannotChangeParameters() {
        when(orders.findOrderByRequestIdAndUserId("stable-operation", 7L)).thenReturn(existing);
        command.setQuantity(3);
        assertThrows(IllegalArgumentException.class, () -> service.createNormalOrder(command));
        verifyNoInteractions(checks);
    }

    @Test void invalidKeyFailsBeforeDatabaseOrRemoteCalls() {
        command.setRequestId("../unsafe");
        assertThrows(IllegalArgumentException.class, () -> service.createNormalOrder(command));
        verifyNoInteractions(orders, checks);
    }

    @Test void sameKeyCannotReplayAnotherPurchaseType() {
        existing.setTradeType("GROUP_BUY");
        when(orders.findOrderByRequestIdAndUserId("stable-operation", 7L)).thenReturn(existing);
        assertThrows(IllegalArgumentException.class, () -> service.createNormalOrder(command));
        verifyNoInteractions(checks, business);
    }

    @Test void authenticatedUserScopesTheLookup() {
        command.setUserId(8L);
        when(orders.findOrderByRequestIdAndUserId("stable-operation", 8L)).thenReturn(existing);
        service.createNormalOrder(command);
        verify(orders).findOrderByRequestIdAndUserId("stable-operation", 8L);
        verify(orders, never()).findOrderByRequestIdAndUserId(anyString(), eq(7L));
    }

    @Test void changedConfirmedPriceFailsBeforeStockOccupation() throws Exception {
        command.setExpectedUnitPrice(1800L);
        doAnswer(invocation -> {
            OrderCreateContext context = invocation.getArgument(0);
            PackageTradeSnapshot snapshot = new PackageTradeSnapshot();
            snapshot.setPrice(1900L);
            context.setPackageSnapshot(snapshot);
            return null;
        }).when(checks).checkNormalOrder(any());
        assertThrows(IllegalArgumentException.class, () -> service.createNormalOrder(command));
        verify(orders, never()).saveOrder(any());
        verifyNoInteractions(business);
    }
}

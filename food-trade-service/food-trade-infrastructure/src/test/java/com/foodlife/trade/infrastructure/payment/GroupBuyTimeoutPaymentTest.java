package com.foodlife.trade.infrastructure.payment;

import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.repository.IPaymentOrderRepository;
import com.foodlife.trade.domain.order.payment.service.PaymentCancellationService;
import com.foodlife.trade.domain.order.payment.service.PaymentRefundService;
import com.foodlife.trade.infrastructure.dao.*;
import com.foodlife.trade.infrastructure.dao.po.*;
import com.foodlife.trade.infrastructure.repository.GroupBuyRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class GroupBuyTimeoutPaymentTest {
    private final IGroupBuyActivityMapper activities = mock(IGroupBuyActivityMapper.class);
    private final IGroupBuyTeamMapper teams = mock(IGroupBuyTeamMapper.class);
    private final IGroupBuyOrderListMapper participants = mock(IGroupBuyOrderListMapper.class);
    private final IDiningOrderMapper orders = mock(IDiningOrderMapper.class);
    private final IDiningOrderItemMapper items = mock(IDiningOrderItemMapper.class);
    private final IPaymentOrderRepository payments = mock(IPaymentOrderRepository.class);
    private final PaymentRefundService refunds = mock(PaymentRefundService.class);
    private final PaymentCancellationService cancellations = mock(PaymentCancellationService.class);

    private GroupBuyRepository expiredTeamWithOrder(String status) {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                GroupBuyTeamPO.class);
        GroupBuyTeamPO team = new GroupBuyTeamPO();
        team.setId(1L);
        team.setTeamId("TEAM_TEST");
        team.setTeamStatus(GroupBuyStatusConstants.IN_PROGRESS);
        team.setValidEndTime(LocalDateTime.now().minusMinutes(1));
        when(teams.selectOne(any())).thenReturn(team);
        when(teams.update(any(), any())).thenReturn(1);
        GroupBuyOrderListPO participant = new GroupBuyOrderListPO();
        participant.setOrderId(2L);
        participant.setUserId(3L);
        participant.setOrderStatus(status);
        when(participants.selectList(any())).thenReturn(Collections.singletonList(participant));
        return new GroupBuyRepository(activities, teams, participants, orders, items, payments, refunds, cancellations);
    }

    @Test void refundFailureDoesNotMarkBusinessOrderRefunded() {
        GroupBuyRepository repository = expiredTeamWithOrder(GroupBuyStatusConstants.PAID);
        PaymentOrderEntity payment = new PaymentOrderEntity();
        when(payments.findByOrderIdAndUserId(2L, 3L)).thenReturn(payment);
        doThrow(new IllegalStateException("Alipay unavailable"))
                .when(refunds).refundPaidPayment(eq(payment), eq("GROUP_BUY_TIMEOUT"), anyString());
        assertThatThrownBy(() -> repository.compensateTimeoutTeam("TEAM_TEST", LocalDateTime.now()))
                .hasMessage("Alipay unavailable");
        verify(orders, never()).update(any(), any());
    }

    @Test void closeFailureDoesNotCancelUnpaidBusinessOrder() {
        GroupBuyRepository repository = expiredTeamWithOrder(GroupBuyStatusConstants.LOCKED);
        doThrow(new IllegalStateException("Alipay close failed"))
                .when(cancellations).closeBeforeCancellation(2L, 3L);
        assertThatThrownBy(() -> repository.compensateTimeoutTeam("TEAM_TEST", LocalDateTime.now()))
                .hasMessage("Alipay close failed");
        verify(orders, never()).update(any(), any());
    }
}

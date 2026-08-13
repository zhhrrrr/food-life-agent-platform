package com.foodlife.trade.domain.order.service.settlement;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.constant.TradeTypeConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderPaySettlementService {

    private final IOrderRepository orderRepository;
    private final IGroupBuyRepository groupBuyRepository;
    private final ISeckillRepository seckillRepository;
    private final IBusinessPackagePort businessPackagePort;
    private final BusinessLinkedList<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> orderPaySettlementRuleFilter;

    public OrderPaySettlementService(IOrderRepository orderRepository,
                                     IGroupBuyRepository groupBuyRepository,
                                     ISeckillRepository seckillRepository,
                                     IBusinessPackagePort businessPackagePort,
                                     @Qualifier("orderPaySettlementRuleFilter")
                                     BusinessLinkedList<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> orderPaySettlementRuleFilter) {
        this.orderRepository = orderRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.seckillRepository = seckillRepository;
        this.businessPackagePort = businessPackagePort;
        this.orderPaySettlementRuleFilter = orderPaySettlementRuleFilter;
    }

    public OrderPaySettlementEntity settlementOrderPaySuccess(OrderPaySuccessEntity orderPaySuccessEntity) {
        try {
            OrderSettlementRuleFilterBackEntity filterBackEntity = orderPaySettlementRuleFilter.apply(
                    buildCommand(orderPaySuccessEntity),
                    new OrderSettlementRuleFilterFactory.DynamicContext()
            );

            DiningOrderEntity order = filterBackEntity.getOrder();
            if (TradeTypeConstants.GROUP_BUY.equals(order.getTradeType())) {
                GroupBuyTeamEntity team = groupBuyRepository.settlementGroupBuyPaySuccess(order, orderPaySuccessEntity.getOutTradeTime());
                return buildSettlementEntity(orderPaySuccessEntity, order, team);
            }

            if (TradeTypeConstants.SECKILL.equals(order.getTradeType())) {
                Long activityId = seckillRepository.settlementSeckillPaySuccess(order);
                OrderPaySettlementEntity settlementEntity = buildSettlementEntity(orderPaySuccessEntity, order, null);
                settlementEntity.setActivityId(activityId);
                return settlementEntity;
            }

            boolean success = orderRepository.updateOrderStatus(order.getId(), OrderStatusConstants.WAIT_PAY, OrderStatusConstants.PAID);
            if (!success) {
                throw new IllegalArgumentException("order status can not pay");
            }
            businessPackagePort.confirmPackageSold(order.getPackageId(), order.getQuantity());
            return buildSettlementEntity(orderPaySuccessEntity, order, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("order pay settlement failed", e);
        }
    }

    private OrderSettlementRuleCommandEntity buildCommand(OrderPaySuccessEntity orderPaySuccessEntity) {
        OrderSettlementRuleCommandEntity command = new OrderSettlementRuleCommandEntity();
        if (orderPaySuccessEntity != null) {
            command.setSource(orderPaySuccessEntity.getSource());
            command.setChannel(orderPaySuccessEntity.getChannel());
            command.setUserId(orderPaySuccessEntity.getUserId());
            command.setOrderId(orderPaySuccessEntity.getOrderId());
            command.setOutTradeNo(orderPaySuccessEntity.getOutTradeNo());
            command.setOutTradeTime(orderPaySuccessEntity.getOutTradeTime());
        }
        return command;
    }

    private OrderPaySettlementEntity buildSettlementEntity(OrderPaySuccessEntity paySuccessEntity, DiningOrderEntity order, GroupBuyTeamEntity team) {
        OrderPaySettlementEntity settlementEntity = new OrderPaySettlementEntity();
        settlementEntity.setSource(paySuccessEntity.getSource());
        settlementEntity.setChannel(paySuccessEntity.getChannel());
        settlementEntity.setUserId(paySuccessEntity.getUserId());
        settlementEntity.setOrderId(order.getId());
        settlementEntity.setOrderNo(order.getOrderNo());
        settlementEntity.setOrderStatus(OrderStatusConstants.PAID);
        settlementEntity.setOutTradeNo(paySuccessEntity.getOutTradeNo());
        settlementEntity.setOutTradeTime(paySuccessEntity.getOutTradeTime());
        if (team != null) {
            settlementEntity.setTeamId(team.getTeamId());
            settlementEntity.setActivityId(team.getActivityId());
            settlementEntity.setTeamStatus(team.getTeamStatus());
            settlementEntity.setTargetCount(team.getTargetCount());
            settlementEntity.setLockCount(team.getLockCount());
            settlementEntity.setCompleteCount(team.getCompleteCount());
        }
        return settlementEntity;
    }
}

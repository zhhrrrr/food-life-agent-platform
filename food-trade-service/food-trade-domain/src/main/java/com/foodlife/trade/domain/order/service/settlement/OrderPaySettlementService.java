package com.foodlife.trade.domain.order.service.settlement;

import com.foodlife.patterns.framework.link.model2.chain.BusinessLinkedList;
import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderPaySettlementEntity;
import com.foodlife.trade.domain.order.model.OrderPaySuccessEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleCommandEntity;
import com.foodlife.trade.domain.order.model.OrderSettlementRuleFilterBackEntity;
import com.foodlife.trade.domain.order.repository.IOrderRepository;
import com.foodlife.trade.domain.order.settlement.factory.OrderSettlementRuleFilterFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderPaySettlementService {

    private final IOrderRepository orderRepository;
    private final BusinessLinkedList<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> orderPaySettlementRuleFilter;

    public OrderPaySettlementService(IOrderRepository orderRepository,
                                     @Qualifier("orderPaySettlementRuleFilter")
                                     BusinessLinkedList<OrderSettlementRuleCommandEntity, OrderSettlementRuleFilterFactory.DynamicContext, OrderSettlementRuleFilterBackEntity> orderPaySettlementRuleFilter) {
        this.orderRepository = orderRepository;
        this.orderPaySettlementRuleFilter = orderPaySettlementRuleFilter;
    }

    public OrderPaySettlementEntity settlementOrderPaySuccess(OrderPaySuccessEntity orderPaySuccessEntity) {
        try {
            OrderSettlementRuleFilterBackEntity filterBackEntity = orderPaySettlementRuleFilter.apply(
                    buildCommand(orderPaySuccessEntity),
                    new OrderSettlementRuleFilterFactory.DynamicContext()
            );

            DiningOrderEntity order = filterBackEntity.getOrder();
            boolean success = orderRepository.updateOrderStatus(order.getId(), OrderStatusConstants.WAIT_PAY, OrderStatusConstants.PAID);
            if (!success) {
                throw new IllegalArgumentException("order status can not pay");
            }
            return buildSettlementEntity(orderPaySuccessEntity, order);
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

    private OrderPaySettlementEntity buildSettlementEntity(OrderPaySuccessEntity paySuccessEntity, DiningOrderEntity order) {
        OrderPaySettlementEntity settlementEntity = new OrderPaySettlementEntity();
        settlementEntity.setSource(paySuccessEntity.getSource());
        settlementEntity.setChannel(paySuccessEntity.getChannel());
        settlementEntity.setUserId(paySuccessEntity.getUserId());
        settlementEntity.setOrderId(order.getId());
        settlementEntity.setOrderNo(order.getOrderNo());
        settlementEntity.setOrderStatus(OrderStatusConstants.PAID);
        settlementEntity.setOutTradeNo(paySuccessEntity.getOutTradeNo());
        settlementEntity.setOutTradeTime(paySuccessEntity.getOutTradeTime());
        return settlementEntity;
    }
}

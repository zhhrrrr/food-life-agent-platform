package com.foodlife.trade.domain.order.groupbuy.refund.strategy;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.groupbuy.model.GroupBuyTeamEntity;
import com.foodlife.trade.domain.order.groupbuy.repository.IGroupBuyRepository;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;

public abstract class AbstractGroupBuyRefundStrategy implements IGroupBuyRefundStrategy {

    protected final IGroupBuyRepository groupBuyRepository;

    protected AbstractGroupBuyRefundStrategy(IGroupBuyRepository groupBuyRepository) {
        this.groupBuyRepository = groupBuyRepository;
    }

    protected OrderRefundBehaviorEntity buildSuccessBehavior(OrderRefundCommandEntity command,
                                                             DiningOrderEntity order,
                                                             GroupBuyTeamEntity team) {
        OrderRefundBehaviorEntity behavior = new OrderRefundBehaviorEntity();
        behavior.setSource(command.getSource());
        behavior.setChannel(command.getChannel());
        behavior.setUserId(command.getUserId());
        behavior.setOrderId(order.getId());
        behavior.setOrderNo(order.getOrderNo());
        behavior.setOrderStatus(OrderStatusConstants.REFUNDED);
        behavior.setRefundBehavior(OrderRefundBehaviorEntity.RefundBehaviorEnum.SUCCESS);
        behavior.setTeamId(team.getTeamId());
        behavior.setActivityId(team.getActivityId());
        behavior.setTeamStatus(team.getTeamStatus());
        behavior.setTargetCount(team.getTargetCount());
        behavior.setLockCount(team.getLockCount());
        behavior.setCompleteCount(team.getCompleteCount());
        return behavior;
    }
}

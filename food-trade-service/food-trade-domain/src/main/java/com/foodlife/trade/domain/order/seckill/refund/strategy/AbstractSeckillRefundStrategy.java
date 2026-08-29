package com.foodlife.trade.domain.order.seckill.refund.strategy;

import com.foodlife.trade.domain.order.constant.OrderStatusConstants;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;

public abstract class AbstractSeckillRefundStrategy implements ISeckillRefundStrategy {

    protected final ISeckillRepository seckillRepository;

    protected AbstractSeckillRefundStrategy(ISeckillRepository seckillRepository) {
        this.seckillRepository = seckillRepository;
    }

    protected OrderRefundBehaviorEntity buildSuccessBehavior(OrderRefundCommandEntity command,
                                                             DiningOrderEntity order,
                                                             SeckillOrderEntity seckillOrder) {
        OrderRefundBehaviorEntity behavior = new OrderRefundBehaviorEntity();
        behavior.setSource(command.getSource());
        behavior.setChannel(command.getChannel());
        behavior.setUserId(command.getUserId());
        behavior.setOrderId(order.getId());
        behavior.setOrderNo(order.getOrderNo());
        behavior.setOrderStatus(OrderStatusConstants.REFUNDED);
        behavior.setRefundBehavior(OrderRefundBehaviorEntity.RefundBehaviorEnum.SUCCESS);
        behavior.setActivityId(seckillOrder.getActivityId());
        return behavior;
    }
}

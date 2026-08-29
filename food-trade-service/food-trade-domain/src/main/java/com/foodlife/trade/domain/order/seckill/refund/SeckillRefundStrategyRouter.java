package com.foodlife.trade.domain.order.seckill.refund;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.seckill.refund.strategy.ISeckillRefundStrategy;
import org.springframework.stereotype.Service;

@Service
public class SeckillRefundStrategyRouter {

    private final ISeckillRefundStrategy paidSeckillRefundStrategy;

    public SeckillRefundStrategyRouter(ISeckillRefundStrategy paidSeckillRefundStrategy) {
        this.paidSeckillRefundStrategy = paidSeckillRefundStrategy;
    }

    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        return paidSeckillRefundStrategy.refundOrder(command, order);
    }
}

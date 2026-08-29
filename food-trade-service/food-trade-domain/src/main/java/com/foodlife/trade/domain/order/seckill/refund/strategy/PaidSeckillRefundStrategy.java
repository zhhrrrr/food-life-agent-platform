package com.foodlife.trade.domain.order.seckill.refund.strategy;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import org.springframework.stereotype.Service;

@Service
public class PaidSeckillRefundStrategy extends AbstractSeckillRefundStrategy {

    public PaidSeckillRefundStrategy(ISeckillRepository seckillRepository) {
        super(seckillRepository);
    }

    @Override
    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        SeckillOrderEntity seckillOrder = seckillRepository.refundPaidSeckillOrder(order);
        return buildSuccessBehavior(command, order, seckillOrder);
    }
}

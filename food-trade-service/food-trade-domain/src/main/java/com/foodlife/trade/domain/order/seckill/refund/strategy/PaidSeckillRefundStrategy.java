package com.foodlife.trade.domain.order.seckill.refund.strategy;

import com.foodlife.trade.domain.order.model.DiningOrderEntity;
import com.foodlife.trade.domain.order.model.OrderRefundBehaviorEntity;
import com.foodlife.trade.domain.order.model.OrderRefundCommandEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderEntity;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillRepository;
import com.foodlife.trade.domain.order.seckill.repository.ISeckillStockRepository;
import org.springframework.stereotype.Service;

@Service
public class PaidSeckillRefundStrategy extends AbstractSeckillRefundStrategy {

    private final ISeckillStockRepository seckillStockRepository;

    public PaidSeckillRefundStrategy(ISeckillRepository seckillRepository,
                                     ISeckillStockRepository seckillStockRepository) {
        super(seckillRepository);
        this.seckillStockRepository = seckillStockRepository;
    }

    @Override
    public OrderRefundBehaviorEntity refundOrder(OrderRefundCommandEntity command, DiningOrderEntity order) {
        SeckillOrderEntity seckillOrder = seckillRepository.refundPaidSeckillOrder(order);
        seckillStockRepository.releaseActivityStock(seckillOrder.getActivityId(), order.getUserId());
        return buildSuccessBehavior(command, order, seckillOrder);
    }
}

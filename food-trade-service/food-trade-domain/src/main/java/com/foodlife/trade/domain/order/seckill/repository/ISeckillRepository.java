package com.foodlife.trade.domain.order.seckill.repository;

import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillActivityView;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderAggregate;
import com.foodlife.trade.domain.order.seckill.model.SeckillOrderResult;
import com.foodlife.trade.domain.order.model.DiningOrderEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ISeckillRepository {

    SeckillActivityEntity queryActivityById(Long activityId);

    List<SeckillActivityView> listAvailableActivities(Long packageId, LocalDateTime now, int limit);

    int queryUserTakeCount(Long activityId, Long userId);

    SeckillOrderResult saveSeckillOrder(SeckillOrderAggregate aggregate, LocalDateTime now);

    Long querySeckillActivityId(DiningOrderEntity order);

    Long settlementSeckillPaySuccess(DiningOrderEntity order);

    void cancelUnpaidSeckillOrder(DiningOrderEntity order);
}

package com.foodlife.trade.domain.order.seckill.repository;

import com.foodlife.trade.domain.order.seckill.model.SeckillActivityEntity;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockOccupyResult;
import com.foodlife.trade.domain.order.seckill.model.SeckillStockPreheatResult;

import java.time.LocalDateTime;

public interface ISeckillStockRepository {

    SeckillStockPreheatResult preheatActivityStock(SeckillActivityEntity activity, LocalDateTime now);

    SeckillStockOccupyResult occupyActivityStock(SeckillActivityEntity activity, Long userId, LocalDateTime now);

    void releaseActivityStock(Long activityId, Long userId);

    void refreshActivityStock(SeckillActivityEntity activity, LocalDateTime now, Integer stock);

    Integer queryActivityStock(Long activityId);
}

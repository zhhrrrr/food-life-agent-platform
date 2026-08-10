package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

@Data
public class SeckillStockPreheatResult {

    private Long activityId;
    private Integer dbStock;
    private Integer redisStock;
    private String stockKey;
    private String userKey;
}

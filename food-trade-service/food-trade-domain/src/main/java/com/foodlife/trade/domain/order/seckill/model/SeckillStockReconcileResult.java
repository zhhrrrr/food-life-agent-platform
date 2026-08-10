package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillStockReconcileResult implements Serializable {

    private Long activityId;
    private Integer dbStock;
    private Integer redisStockBefore;
    private Integer redisStockAfter;
    private Integer waitPayCount;
    private Integer paidCount;
    private Boolean refreshed;
}

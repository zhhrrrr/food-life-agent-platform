package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillActivityView implements Serializable {

    private Long activityId;
    private Long packageId;
    private String activityName;
    private Long seckillPrice;
    private Integer activityStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer stock;
    private Integer userTakeLimit;
    private Boolean canBuy;
}

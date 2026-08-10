package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillActivityEntity implements Serializable {

    private Long id;
    private Long packageId;
    private String activityName;
    private Long seckillPrice;
    private Integer activityStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer stock;
    private Integer userTakeLimit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

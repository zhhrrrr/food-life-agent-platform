package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyActivityEntity implements Serializable {

    private Long id;
    private Long packageId;
    private String activityName;
    private Integer targetCount;
    private Integer userTakeLimit;
    private Long groupPrice;
    private Integer activityStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

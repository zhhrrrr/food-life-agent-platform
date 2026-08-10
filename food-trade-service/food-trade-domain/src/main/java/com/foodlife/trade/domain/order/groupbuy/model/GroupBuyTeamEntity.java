package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyTeamEntity implements Serializable {

    private Long id;
    private String teamId;
    private Long activityId;
    private Long packageId;
    private Integer targetCount;
    private Integer completeCount;
    private Integer lockCount;
    private String teamStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

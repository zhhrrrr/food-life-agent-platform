package com.foodlife.trade.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyActivityResponseDTO implements Serializable {
    private Long activityId;
    private Long packageId;
    private String activityName;
    private Long groupPrice;
    private Integer targetCount;
    private Integer stock;
    private Integer userTakeLimit;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Boolean canBuy;
}

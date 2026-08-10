package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SeckillActivityListResponseDTO implements Serializable {

    private List<ActivityInfo> activities = new ArrayList<>();

    @Data
    public static class ActivityInfo implements Serializable {
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
}

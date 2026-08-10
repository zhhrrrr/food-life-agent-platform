package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyTeamDetailResponseDTO implements Serializable {

    private String teamId;
    private Long activityId;
    private Long packageId;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;
    private Integer remainingCount;
    private String teamStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Boolean canJoin;
    private List<ParticipantInfo> participants = new ArrayList<>();

    @Data
    public static class ParticipantInfo implements Serializable {
        private Long userId;
        private Long orderId;
        private String orderNo;
        private String groupBuyOrderStatus;
        private String orderStatus;
        private LocalDateTime outTradeTime;
        private LocalDateTime createTime;
    }
}

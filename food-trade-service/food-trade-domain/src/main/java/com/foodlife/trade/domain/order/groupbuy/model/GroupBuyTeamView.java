package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyTeamView implements Serializable {

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
    private List<GroupBuyParticipantView> participants = new ArrayList<>();
}

package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupBuyTimeoutCompensationResponseDTO implements Serializable {

    private LocalDateTime compensateTime;
    private Integer scannedTeamCount;
    private Integer compensatedTeamCount;
    private Integer canceledOrderCount;
    private Integer refundedOrderCount;
    private Integer restoredStockCount;
    private List<Detail> details = new ArrayList<>();

    @Data
    public static class Detail implements Serializable {
        private String teamId;
        private Long activityId;
        private String teamStatus;
        private Integer beforeLockCount;
        private Integer beforeCompleteCount;
        private Integer canceledOrderCount;
        private Integer refundedOrderCount;
        private Integer restoredStockCount;
    }
}

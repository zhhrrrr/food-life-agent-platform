package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NormalOrderTimeoutCancelResponseDTO implements Serializable {

    private LocalDateTime compensateTime;
    private Integer timeoutMinutes;
    private LocalDateTime timeoutBefore;
    private Integer scannedOrderCount;
    private Integer canceledOrderCount;
    private Integer releaseStockMessageCount;
    private Integer failedOrderCount;
    private List<Detail> details = new ArrayList<>();

    @Data
    public static class Detail implements Serializable {
        private Long orderId;
        private String orderNo;
        private Long userId;
        private Long packageId;
        private Integer quantity;
        private String beforeOrderStatus;
        private String afterOrderStatus;
        private Boolean canceled;
        private Boolean couponReleased;
        private Boolean releaseStockMessageSent;
        private String failReason;
    }
}

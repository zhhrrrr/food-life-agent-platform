package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReceiveCouponResponseDTO implements Serializable {

    private Long userCouponId;
    private Long templateId;
    private Long userId;
    private String couponName;
    private Long thresholdAmount;
    private Long discountAmount;
    private String couponStatus;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
}

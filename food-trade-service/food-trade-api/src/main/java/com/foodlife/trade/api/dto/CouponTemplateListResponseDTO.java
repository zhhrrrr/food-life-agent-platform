package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CouponTemplateListResponseDTO implements Serializable {

    private List<CouponTemplateInfo> templates;

    @Data
    public static class CouponTemplateInfo implements Serializable {
        private Long templateId;
        private String couponName;
        private String couponType;
        private Long thresholdAmount;
        private Long discountAmount;
        private LocalDateTime validStartTime;
        private LocalDateTime validEndTime;
        private Integer totalStock;
        private Integer receivedCount;
        private Integer templateStatus;
    }
}

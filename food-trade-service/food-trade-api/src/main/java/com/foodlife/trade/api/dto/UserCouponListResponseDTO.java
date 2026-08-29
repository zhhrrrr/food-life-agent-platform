package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserCouponListResponseDTO implements Serializable {

    private List<UserCouponInfo> coupons;

    @Data
    public static class UserCouponInfo implements Serializable {
        private Long userCouponId;
        private Long templateId;
        private Long userId;
        private String couponName;
        private String couponType;
        private Long thresholdAmount;
        private Long discountAmount;
        private String scopeType;
        private Long scopeShopId;
        private Long scopePackageId;
        private String couponStatus;
        private Long usedOrderId;
        private LocalDateTime validStartTime;
        private LocalDateTime validEndTime;
        private LocalDateTime receiveTime;
        private LocalDateTime useTime;
    }
}

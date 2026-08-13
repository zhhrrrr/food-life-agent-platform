package com.foodlife.trade.domain.order.coupon.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserCouponEntity implements Serializable {

    private Long id;
    private Long templateId;
    private Long userId;
    private String couponName;
    private String couponType;
    private Long thresholdAmount;
    private Long discountAmount;
    private String couponStatus;
    private Long usedOrderId;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

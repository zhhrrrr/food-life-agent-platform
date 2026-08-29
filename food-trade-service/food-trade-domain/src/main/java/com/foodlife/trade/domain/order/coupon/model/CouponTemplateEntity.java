package com.foodlife.trade.domain.order.coupon.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponTemplateEntity implements Serializable {

    private Long id;
    private String couponName;
    private String couponType;
    private Long thresholdAmount;
    private Long discountAmount;
    private String scopeType;
    private Long scopeShopId;
    private Long scopePackageId;
    private Integer userReceiveLimit;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer totalStock;
    private Integer receivedCount;
    private Integer templateStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

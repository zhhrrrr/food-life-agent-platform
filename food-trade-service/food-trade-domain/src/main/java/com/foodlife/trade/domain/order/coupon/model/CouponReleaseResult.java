package com.foodlife.trade.domain.order.coupon.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CouponReleaseResult implements Serializable {

    private Long userCouponId;
    private Boolean released;
    private String couponStatus;
}

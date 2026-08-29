package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RefundOrderResponseDTO implements Serializable {

    private String source;
    private String channel;
    private Long userId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String refundBehavior;
    private Long userCouponId;
    private Boolean couponReturned;
    private String couponReturnStatus;
    private String teamId;
    private Long activityId;
    private String teamStatus;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;
}

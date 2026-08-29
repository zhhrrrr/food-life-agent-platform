package com.foodlife.trade.domain.order.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
public class OrderRefundBehaviorEntity implements Serializable {

    private String source;
    private String channel;
    private Long userId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private RefundBehaviorEnum refundBehavior;
    private Long userCouponId;
    private Boolean couponReturned;
    private String couponReturnStatus;
    private String teamId;
    private Long activityId;
    private String teamStatus;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public enum RefundBehaviorEnum {
        SUCCESS("success", "success"),
        REPEAT("repeat", "repeat");

        private String code;
        private String info;
    }
}

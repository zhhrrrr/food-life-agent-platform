package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupBuyLockResult implements Serializable {

    private Long orderId;
    private String orderNo;
    private String teamId;
    private Long activityId;
    private Long payAmount;
    private String orderStatus;
    private String teamStatus;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;
}

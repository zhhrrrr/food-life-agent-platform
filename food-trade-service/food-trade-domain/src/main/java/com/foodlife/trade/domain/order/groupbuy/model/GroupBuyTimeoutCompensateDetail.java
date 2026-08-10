package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupBuyTimeoutCompensateDetail implements Serializable {

    private String teamId;
    private Long activityId;
    private String teamStatus;
    private Integer beforeLockCount;
    private Integer beforeCompleteCount;
    private Integer canceledOrderCount;
    private Integer refundedOrderCount;
    private Integer restoredStockCount;
}

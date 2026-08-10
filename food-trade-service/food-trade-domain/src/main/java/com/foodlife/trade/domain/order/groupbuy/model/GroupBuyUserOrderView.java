package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyUserOrderView implements Serializable {

    private Long groupBuyOrderListId;
    private Long userId;
    private String teamId;
    private Long orderId;
    private String orderNo;
    private Long activityId;
    private Long packageId;
    private Long payAmount;
    private String groupBuyOrderStatus;
    private String orderStatus;
    private String teamStatus;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;
    private Integer remainingCount;
    private LocalDateTime outTradeTime;
    private LocalDateTime validEndTime;
    private LocalDateTime createTime;
    private Boolean canPay;
    private Boolean canCancel;
    private Boolean canRefund;
}

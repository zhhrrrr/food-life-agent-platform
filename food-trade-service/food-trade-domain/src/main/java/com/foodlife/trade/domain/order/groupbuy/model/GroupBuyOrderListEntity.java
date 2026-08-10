package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyOrderListEntity implements Serializable {

    private Long id;
    private Long userId;
    private String teamId;
    private Long orderId;
    private String orderNo;
    private Long activityId;
    private Long packageId;
    private String orderStatus;
    private LocalDateTime outTradeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestResult implements Serializable {

    private String requestNo;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Integer quantity;
    private Long orderId;
    private String orderNo;
    private String requestStatus;
    private Integer remainingStock;
    private String failReason;
}

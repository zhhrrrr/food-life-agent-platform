package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderResult implements Serializable {

    private Long activityId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private Long payAmount;
    private String orderStatus;
    private Integer remainingStock;
}

package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateSeckillOrderResponseDTO implements Serializable {

    private Long activityId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private Long payAmount;
    private String orderStatus;
    private Integer remainingStock;
}

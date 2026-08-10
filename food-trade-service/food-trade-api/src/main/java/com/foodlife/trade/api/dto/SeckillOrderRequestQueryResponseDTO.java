package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderRequestQueryResponseDTO implements Serializable {

    private String requestNo;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Integer quantity;
    private Long orderId;
    private String orderNo;
    private String requestStatus;
    private String failReason;
}

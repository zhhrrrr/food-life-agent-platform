package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateSeckillOrderRequestResponseDTO implements Serializable {

    private String requestNo;
    private Long activityId;
    private Long packageId;
    private Integer quantity;
    private String requestStatus;
    private Integer remainingStock;
}

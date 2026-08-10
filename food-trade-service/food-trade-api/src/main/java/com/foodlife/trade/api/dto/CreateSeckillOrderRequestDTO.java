package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateSeckillOrderRequestDTO implements Serializable {

    private Long activityId;
    private Integer quantity;
}

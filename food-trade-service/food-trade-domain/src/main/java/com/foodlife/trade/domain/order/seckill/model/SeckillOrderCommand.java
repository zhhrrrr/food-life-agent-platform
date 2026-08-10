package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderCommand implements Serializable {

    private Long userId;
    private Long activityId;
    private Integer quantity;
}

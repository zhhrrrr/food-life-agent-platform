package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillStockPreheatResponseDTO implements Serializable {

    private Long activityId;
    private Integer dbStock;
    private Integer redisStock;
    private String stockKey;
    private String userKey;
}

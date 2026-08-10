package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillOrderEntity implements Serializable {

    private Long id;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

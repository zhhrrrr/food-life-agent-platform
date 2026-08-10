package com.foodlife.trade.domain.order.seckill.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillOrderRequestEntity implements Serializable {

    private Long id;
    private String requestNo;
    private Long userId;
    private Long activityId;
    private Long packageId;
    private Integer quantity;
    private Long orderId;
    private String orderNo;
    private String requestStatus;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

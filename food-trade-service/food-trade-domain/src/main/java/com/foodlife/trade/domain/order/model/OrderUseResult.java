package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderUseResult implements Serializable {

    private Long userId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String useBehavior;
    private LocalDateTime useTime;
}

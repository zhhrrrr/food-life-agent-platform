package com.foodlife.trade.domain.order.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderTimeoutCloseMessage implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private String tradeType;
    private LocalDateTime orderCreateTime;
}

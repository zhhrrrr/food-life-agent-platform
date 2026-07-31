package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderRefundCommandEntity implements Serializable {

    private String source;
    private String channel;
    private Long userId;
    private Long orderId;
}

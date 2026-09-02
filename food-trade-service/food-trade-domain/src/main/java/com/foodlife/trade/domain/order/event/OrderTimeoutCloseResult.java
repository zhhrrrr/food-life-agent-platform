package com.foodlife.trade.domain.order.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderTimeoutCloseResult implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private String tradeType;
    private String beforeOrderStatus;
    private String afterOrderStatus;
    private String payOrderNo;
    private String beforePayStatus;
    private String afterPayStatus;
    private Boolean orderCanceled;
    private Boolean paymentClosed;
    private String closeSource;
    private String skipReason;
}

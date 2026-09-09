package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RefundOrderEntity implements Serializable {

    private Long id;
    private String refundOrderNo;
    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String source;
    private String channel;
    private Long refundAmount;
    private String refundStatus;
    private String refundReason;
    private String outTradeNo;
    private String outRefundNo;
    private String failReason;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


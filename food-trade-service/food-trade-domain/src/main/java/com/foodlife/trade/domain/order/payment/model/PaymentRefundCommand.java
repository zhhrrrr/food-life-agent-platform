package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentRefundCommand implements Serializable {

    private String refundOrderNo;
    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String source;
    private String channel;
    private Long refundAmount;
    private String refundReason;
    private String outTradeNo;
}


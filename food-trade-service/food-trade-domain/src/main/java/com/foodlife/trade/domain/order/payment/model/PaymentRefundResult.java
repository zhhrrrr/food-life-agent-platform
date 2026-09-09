package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PaymentRefundResult implements Serializable {

    private String refundOrderNo;
    private String outRefundNo;
    private String refundStatus;
    private String failReason;
    private LocalDateTime refundTime;
    private String rawResult;
}


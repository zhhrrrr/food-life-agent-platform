package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentOrderTimeoutCloseDetail implements Serializable {

    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long payAmount;
    private String beforePayStatus;
    private String afterPayStatus;
    private Boolean paymentClosed;
    private String beforeOrderStatus;
    private String afterOrderStatus;
    private Boolean orderCanceled;
    private String failReason;
}

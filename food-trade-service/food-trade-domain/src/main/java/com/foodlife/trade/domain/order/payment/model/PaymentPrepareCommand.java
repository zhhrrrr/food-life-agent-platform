package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentPrepareCommand implements Serializable {

    private Long userId;
    private Long orderId;
    private String source;
    private String channel;
}

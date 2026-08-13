package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PaymentCallbackCommand implements Serializable {

    private String payOrderNo;
    private String outTradeNo;
    private Long payAmount;
    private LocalDateTime payTime;
}

package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PaymentCallbackRequestDTO implements Serializable {

    private String payOrderNo;
    private String outTradeNo;
    private Long payAmount;
    private LocalDateTime payTime;
}

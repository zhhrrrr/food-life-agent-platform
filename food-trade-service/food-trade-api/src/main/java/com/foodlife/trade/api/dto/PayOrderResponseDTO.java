package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PayOrderResponseDTO implements Serializable {

    private String source;
    private String channel;
    private Long userId;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String outTradeNo;
    private LocalDateTime outTradeTime;
}

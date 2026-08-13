package com.foodlife.trade.domain.order.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PaymentOrderEntity implements Serializable {

    private Long id;
    private String payOrderNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String source;
    private String channel;
    private Long payAmount;
    private String payStatus;
    private String outTradeNo;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderSettlementRuleCommandEntity implements Serializable {

    private String source;
    private String channel;
    private Long userId;
    private Long orderId;
    private String outTradeNo;
    private LocalDateTime outTradeTime;
}

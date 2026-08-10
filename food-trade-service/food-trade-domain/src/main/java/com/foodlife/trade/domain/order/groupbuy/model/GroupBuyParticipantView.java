package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GroupBuyParticipantView implements Serializable {

    private Long userId;
    private Long orderId;
    private String orderNo;
    private String groupBuyOrderStatus;
    private String orderStatus;
    private LocalDateTime outTradeTime;
    private LocalDateTime createTime;
}

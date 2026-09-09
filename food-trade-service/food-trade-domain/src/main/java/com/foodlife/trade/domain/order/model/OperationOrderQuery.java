package com.foodlife.trade.domain.order.model;

import lombok.Data;

@Data
public class OperationOrderQuery {

    private Long userId;
    private Long orderId;
    private String orderNo;
    private String tradeType;
    private String orderStatus;
    private Integer pageSize;
}

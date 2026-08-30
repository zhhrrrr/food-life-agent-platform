package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderListResult implements Serializable {

    private List<OrderSummaryEntity> orders;
    private Boolean hasMore;
    private Long lastId;
    private String tradeType;
    private String orderStatus;
}

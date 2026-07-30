package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateOrderResult implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long payAmount;
    private String orderStatus;
}

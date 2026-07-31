package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CancelOrderResult implements Serializable {

    private Long orderId;
    private String orderNo;
    private String orderStatus;
}

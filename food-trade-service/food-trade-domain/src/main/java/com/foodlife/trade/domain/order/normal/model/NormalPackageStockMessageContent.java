package com.foodlife.trade.domain.order.normal.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class NormalPackageStockMessageContent implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long packageId;
    private Integer quantity;
    private String actionType;
}

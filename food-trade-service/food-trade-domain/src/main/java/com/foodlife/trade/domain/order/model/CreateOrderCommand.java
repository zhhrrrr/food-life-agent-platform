package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateOrderCommand implements Serializable {

    private Long userId;
    private Long packageId;
    private Integer quantity;
    private Long userCouponId;
}

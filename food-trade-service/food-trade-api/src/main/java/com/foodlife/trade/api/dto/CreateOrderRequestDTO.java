package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateOrderRequestDTO implements Serializable {

    private Long packageId;
    private Integer quantity;
    private Long userCouponId;
    /** Optional stable client key; scoped to the authenticated user. */
    private String requestId;
    private Long expectedUnitPrice;
}

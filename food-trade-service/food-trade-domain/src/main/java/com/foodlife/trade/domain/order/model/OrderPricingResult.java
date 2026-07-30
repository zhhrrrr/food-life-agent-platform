package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderPricingResult implements Serializable {

    private Long totalAmount;
    private Long payAmount;
}

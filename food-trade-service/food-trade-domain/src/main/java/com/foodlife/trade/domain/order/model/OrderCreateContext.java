package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderCreateContext implements Serializable {

    private String tradeType;
    private CreateOrderCommand command;
    private PackageTradeSnapshot packageSnapshot;
    private com.foodlife.trade.domain.order.coupon.model.UserCouponEntity userCoupon;
}

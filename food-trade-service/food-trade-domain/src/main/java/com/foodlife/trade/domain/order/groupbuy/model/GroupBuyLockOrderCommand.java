package com.foodlife.trade.domain.order.groupbuy.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupBuyLockOrderCommand implements Serializable {

    private Long userId;
    private Long packageId;
    private Integer quantity;
    private String teamId;
}

package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderUseCommandEntity implements Serializable {

    private Long userId;
    private Long orderId;
    private String useSource;
}

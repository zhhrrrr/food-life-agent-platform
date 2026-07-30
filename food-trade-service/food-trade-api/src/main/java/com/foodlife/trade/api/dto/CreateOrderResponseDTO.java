package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateOrderResponseDTO implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long payAmount;
    private String orderStatus;
}

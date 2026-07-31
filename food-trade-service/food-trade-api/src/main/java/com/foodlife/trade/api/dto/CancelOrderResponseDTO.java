package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CancelOrderResponseDTO implements Serializable {

    private Long orderId;
    private String orderNo;
    private String orderStatus;
}

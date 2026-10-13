package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateGroupBuyOrderRequestDTO implements Serializable {

    private Long packageId;
    private Integer quantity;
    private String requestId;
    private Long expectedUnitPrice;
    private Long expectedActivityId;

    private String teamId;
}

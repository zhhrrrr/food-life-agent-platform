package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeataPackageStockAdjustRequestDTO implements Serializable {

    private Long packageId;
    private Integer adjustQuantity;
    private String reason;
    private String operationId;
}

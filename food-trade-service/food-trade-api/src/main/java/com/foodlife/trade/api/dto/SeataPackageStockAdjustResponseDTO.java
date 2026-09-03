package com.foodlife.trade.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeataPackageStockAdjustResponseDTO implements Serializable {

    private String operationId;
    private Long operatorId;
    private Long packageId;
    private Integer adjustQuantity;
    private Integer stock;
    private Integer sold;
    private String txStatus;
}

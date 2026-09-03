package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdjustPackageStockResponseDTO implements Serializable {

    private Long packageId;
    private Long operatorId;
    private Integer adjustQuantity;
    private Integer stock;
    private Integer sold;
    private String changeType;
    private String operationId;
}

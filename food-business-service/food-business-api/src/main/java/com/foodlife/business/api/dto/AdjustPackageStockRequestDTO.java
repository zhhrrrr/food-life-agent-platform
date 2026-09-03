package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdjustPackageStockRequestDTO implements Serializable {

    private Long operatorId;
    private Integer adjustQuantity;
    private String reason;
    private String operationId;
}

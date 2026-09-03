package com.foodlife.business.domain.packagee.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdjustPackageStockCommand implements Serializable {

    private Long packageId;
    private Long operatorId;
    private Integer adjustQuantity;
    private String reason;
    private String operationId;
}

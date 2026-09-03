package com.foodlife.business.domain.packagee.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdjustPackageStockResult implements Serializable {

    private Long packageId;
    private Long operatorId;
    private Integer adjustQuantity;
    private Integer stock;
    private Integer sold;
    private String changeType;
    private String operationId;
}

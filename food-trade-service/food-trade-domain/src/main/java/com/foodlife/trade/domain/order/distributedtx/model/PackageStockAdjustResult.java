package com.foodlife.trade.domain.order.distributedtx.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageStockAdjustResult implements Serializable {

    private Long packageId;
    private Long operatorId;
    private Integer adjustQuantity;
    private Integer stock;
    private Integer sold;
    private String operationId;
}

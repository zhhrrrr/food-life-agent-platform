package com.foodlife.trade.domain.order.distributedtx.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class DistributedPackageStockAdjustCommand implements Serializable {

    private String operationId;
    private Long operatorId;
    private Long packageId;
    private Integer adjustQuantity;
    private String reason;
}

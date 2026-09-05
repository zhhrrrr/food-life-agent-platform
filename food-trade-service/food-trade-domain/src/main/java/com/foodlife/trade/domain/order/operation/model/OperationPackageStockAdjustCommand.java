package com.foodlife.trade.domain.order.operation.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperationPackageStockAdjustCommand implements Serializable {

    private String operationId;
    private Long operatorId;
    private Long packageId;
    private Integer adjustQuantity;
    private String reason;
}

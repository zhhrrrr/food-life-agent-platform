package com.foodlife.trade.domain.order.normal.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PackageStockChangeRecord implements Serializable {

    private Long id;
    private String operationId;
    private Long packageId;
    private Integer quantity;
    private String changeType;
    private String changeStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

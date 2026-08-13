package com.foodlife.business.domain.packagee.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageStockChangeResult implements Serializable {

    private Long packageId;
    private Integer quantity;
    private Integer stock;
    private Integer sold;
    private String changeType;
}

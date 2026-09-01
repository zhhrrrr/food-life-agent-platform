package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageStockChangeResponseDTO implements Serializable {

    private Long packageId;
    private Integer quantity;
    private Integer stock;
    private Integer sold;
    private String changeType;
}

package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageTradeSnapshotResponseDTO implements Serializable {

    private Long shopId;
    private String shopName;
    private Long packageId;
    private String packageName;
    private String packageDescription;
    private String coverImage;
    private Long price;
    private Long originalPrice;
    private Integer stock;
    private Integer packageStatus;
    private String useRule;
}

package com.foodlife.trade.domain.order.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class PackageTradeSnapshot implements Serializable {

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

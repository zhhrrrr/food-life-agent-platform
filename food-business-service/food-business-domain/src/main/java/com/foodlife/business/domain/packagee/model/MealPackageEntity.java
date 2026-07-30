package com.foodlife.business.domain.packagee.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class MealPackageEntity implements Serializable {

    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private String coverImage;
    private Long price;
    private Long originalPrice;
    private Integer stock;
    private Integer sold;
    private Integer status;
    private String useRule;
}

package com.foodlife.business.domain.shop.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopCategoryEntity implements Serializable {

    private Long id;
    private String name;
    private String icon;
    private Integer sort;
}

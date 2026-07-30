package com.foodlife.business.domain.shop.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopEntity implements Serializable {

    private Long id;
    private String name;
    private Long categoryId;
    private String images;
    private String area;
    private String address;
    private Double longitude;
    private Double latitude;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
}

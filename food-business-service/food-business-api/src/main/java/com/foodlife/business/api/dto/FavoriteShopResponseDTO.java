package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FavoriteShopResponseDTO implements Serializable {

    private Long favoriteId;
    private Long shopId;
    private String shopName;
    private Long categoryId;
    private String images;
    private String area;
    private String address;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private LocalDateTime favoriteTime;
}

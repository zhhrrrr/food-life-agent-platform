package com.foodlife.business.domain.favorite.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopFavoriteCommand implements Serializable {

    private Long userId;
    private Long shopId;
}

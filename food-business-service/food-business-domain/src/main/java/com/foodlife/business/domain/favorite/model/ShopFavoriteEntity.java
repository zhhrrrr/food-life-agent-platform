package com.foodlife.business.domain.favorite.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopFavoriteEntity implements Serializable {

    private Long id;
    private Long userId;
    private Long shopId;
    private Integer favoriteStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ShopFavoriteResponseDTO implements Serializable {

    private Long favoriteId;
    private Long userId;
    private Long shopId;
    private Boolean favorite;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

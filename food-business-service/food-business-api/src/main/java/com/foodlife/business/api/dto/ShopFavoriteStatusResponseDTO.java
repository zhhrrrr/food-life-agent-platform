package com.foodlife.business.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopFavoriteStatusResponseDTO implements Serializable {

    private Long userId;
    private Long shopId;
    private Boolean favorite;
}

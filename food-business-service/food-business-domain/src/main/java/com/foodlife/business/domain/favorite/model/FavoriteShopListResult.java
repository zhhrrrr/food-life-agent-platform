package com.foodlife.business.domain.favorite.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class FavoriteShopListResult implements Serializable {

    private List<FavoriteShopEntity> shops = new ArrayList<>();
    private Boolean hasMore;
    private Long lastId;
}

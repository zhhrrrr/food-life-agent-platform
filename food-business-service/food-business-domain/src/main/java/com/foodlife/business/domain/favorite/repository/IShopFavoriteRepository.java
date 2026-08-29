package com.foodlife.business.domain.favorite.repository;

import com.foodlife.business.domain.favorite.model.FavoriteShopEntity;
import com.foodlife.business.domain.favorite.model.ShopFavoriteEntity;

import java.util.List;

public interface IShopFavoriteRepository {

    ShopFavoriteEntity favoriteShop(Long userId, Long shopId);

    ShopFavoriteEntity unfavoriteShop(Long userId, Long shopId);

    boolean isFavorite(Long userId, Long shopId);

    List<FavoriteShopEntity> listFavoriteShops(Long userId, Long lastId, Integer limit);
}

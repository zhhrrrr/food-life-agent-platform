package com.foodlife.business.domain.shop.repository;

import com.foodlife.business.domain.shop.model.ShopCategoryEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;

import java.util.List;

public interface IShopRepository {

    List<ShopCategoryEntity> listCategories();

    ShopEntity findShopById(Long id);

    List<ShopEntity> listShopsByCategory(Long categoryId, Integer current, Integer size);

    List<ShopEntity> listShopsByName(String name, Integer current, Integer size);
}

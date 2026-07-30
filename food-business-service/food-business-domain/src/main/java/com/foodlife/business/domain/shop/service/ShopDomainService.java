package com.foodlife.business.domain.shop.service;

import com.foodlife.business.domain.shop.model.ShopCategoryEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.repository.IShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopDomainService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final IShopRepository shopRepository;

    public ShopDomainService(IShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public List<ShopCategoryEntity> listCategories() {
        return shopRepository.listCategories();
    }

    public ShopEntity queryShopById(Long id) {
        return shopRepository.findShopById(id);
    }

    public List<ShopEntity> queryShopsByCategory(Long categoryId, Integer current) {
        return shopRepository.listShopsByCategory(categoryId, normalizeCurrent(current), DEFAULT_PAGE_SIZE);
    }

    public List<ShopEntity> queryShopsByName(String name, Integer current) {
        return shopRepository.listShopsByName(name, normalizeCurrent(current), DEFAULT_PAGE_SIZE);
    }

    private Integer normalizeCurrent(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }
}

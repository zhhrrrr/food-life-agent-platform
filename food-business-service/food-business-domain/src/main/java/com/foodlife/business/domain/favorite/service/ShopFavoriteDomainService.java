package com.foodlife.business.domain.favorite.service;

import com.foodlife.business.domain.favorite.model.FavoriteShopListResult;
import com.foodlife.business.domain.favorite.model.ShopFavoriteCommand;
import com.foodlife.business.domain.favorite.model.ShopFavoriteEntity;
import com.foodlife.business.domain.favorite.repository.IShopFavoriteRepository;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.repository.IShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopFavoriteDomainService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final IShopFavoriteRepository shopFavoriteRepository;
    private final IShopRepository shopRepository;

    public ShopFavoriteDomainService(IShopFavoriteRepository shopFavoriteRepository, IShopRepository shopRepository) {
        this.shopFavoriteRepository = shopFavoriteRepository;
        this.shopRepository = shopRepository;
    }

    public ShopFavoriteEntity favoriteShop(ShopFavoriteCommand command) {
        checkCommand(command);
        checkShopOnline(command.getShopId());
        return shopFavoriteRepository.favoriteShop(command.getUserId(), command.getShopId());
    }

    public ShopFavoriteEntity unfavoriteShop(ShopFavoriteCommand command) {
        checkCommand(command);
        return shopFavoriteRepository.unfavoriteShop(command.getUserId(), command.getShopId());
    }

    public boolean isFavorite(Long userId, Long shopId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (shopId == null) {
            throw new IllegalArgumentException("shopId required");
        }
        return shopFavoriteRepository.isFavorite(userId, shopId);
    }

    public FavoriteShopListResult listFavoriteShops(Long userId, Long lastId, Integer pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
        int normalizedPageSize = normalizePageSize(pageSize);
        List<com.foodlife.business.domain.favorite.model.FavoriteShopEntity> shops =
                shopFavoriteRepository.listFavoriteShops(userId, lastId, normalizedPageSize + 1);
        FavoriteShopListResult result = new FavoriteShopListResult();
        boolean hasMore = shops.size() > normalizedPageSize;
        if (hasMore) {
            shops = shops.subList(0, normalizedPageSize);
        }
        result.setShops(shops);
        result.setHasMore(hasMore);
        result.setLastId(shops.isEmpty() ? null : shops.get(shops.size() - 1).getFavoriteId());
        return result;
    }

    private void checkCommand(ShopFavoriteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("favorite command required");
        }
        if (command.getUserId() == null) {
            throw new IllegalArgumentException("user not login");
        }
        if (command.getShopId() == null) {
            throw new IllegalArgumentException("shopId required");
        }
    }

    private void checkShopOnline(Long shopId) {
        ShopEntity shop = shopRepository.findShopById(shopId);
        if (shop == null) {
            throw new IllegalArgumentException("shop not found");
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

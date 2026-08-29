package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodlife.business.domain.favorite.model.FavoriteShopEntity;
import com.foodlife.business.domain.favorite.model.ShopFavoriteEntity;
import com.foodlife.business.domain.favorite.repository.IShopFavoriteRepository;
import com.foodlife.business.infrastructure.dao.IShopFavoriteMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.ShopFavoritePO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ShopFavoriteRepository implements IShopFavoriteRepository {

    private final IShopFavoriteMapper shopFavoriteMapper;
    private final IShopMapper shopMapper;

    public ShopFavoriteRepository(IShopFavoriteMapper shopFavoriteMapper, IShopMapper shopMapper) {
        this.shopFavoriteMapper = shopFavoriteMapper;
        this.shopMapper = shopMapper;
    }

    @Override
    public ShopFavoriteEntity favoriteShop(Long userId, Long shopId) {
        ShopFavoritePO existing = queryByUserAndShop(userId, shopId);
        if (existing != null) {
            if (existing.getFavoriteStatus() == null || existing.getFavoriteStatus() != 1) {
                shopFavoriteMapper.update(null, new LambdaUpdateWrapper<ShopFavoritePO>()
                        .set(ShopFavoritePO::getFavoriteStatus, 1)
                        .eq(ShopFavoritePO::getId, existing.getId()));
                existing = shopFavoriteMapper.selectById(existing.getId());
            }
            return toEntity(existing);
        }

        ShopFavoritePO favorite = new ShopFavoritePO();
        favorite.setUserId(userId);
        favorite.setShopId(shopId);
        favorite.setFavoriteStatus(1);
        try {
            shopFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            shopFavoriteMapper.update(null, new LambdaUpdateWrapper<ShopFavoritePO>()
                    .set(ShopFavoritePO::getFavoriteStatus, 1)
                    .eq(ShopFavoritePO::getUserId, userId)
                    .eq(ShopFavoritePO::getShopId, shopId));
            favorite = queryByUserAndShop(userId, shopId);
            return toEntity(favorite);
        }
        return toEntity(shopFavoriteMapper.selectById(favorite.getId()));
    }

    @Override
    public ShopFavoriteEntity unfavoriteShop(Long userId, Long shopId) {
        ShopFavoritePO existing = queryByUserAndShop(userId, shopId);
        if (existing == null) {
            ShopFavoriteEntity entity = new ShopFavoriteEntity();
            entity.setUserId(userId);
            entity.setShopId(shopId);
            entity.setFavoriteStatus(0);
            return entity;
        }
        if (existing.getFavoriteStatus() == null || existing.getFavoriteStatus() != 0) {
            shopFavoriteMapper.update(null, new LambdaUpdateWrapper<ShopFavoritePO>()
                    .set(ShopFavoritePO::getFavoriteStatus, 0)
                    .eq(ShopFavoritePO::getId, existing.getId()));
        }
        return toEntity(shopFavoriteMapper.selectById(existing.getId()));
    }

    @Override
    public boolean isFavorite(Long userId, Long shopId) {
        Long count = shopFavoriteMapper.selectCount(new LambdaQueryWrapper<ShopFavoritePO>()
                .eq(ShopFavoritePO::getUserId, userId)
                .eq(ShopFavoritePO::getShopId, shopId)
                .eq(ShopFavoritePO::getFavoriteStatus, 1));
        return count != null && count > 0;
    }

    @Override
    public List<FavoriteShopEntity> listFavoriteShops(Long userId, Long lastId, Integer limit) {
        List<ShopFavoritePO> favorites = shopFavoriteMapper.selectPage(new Page<>(1, limit),
                        new LambdaQueryWrapper<ShopFavoritePO>()
                                .eq(ShopFavoritePO::getUserId, userId)
                                .eq(ShopFavoritePO::getFavoriteStatus, 1)
                                .lt(lastId != null, ShopFavoritePO::getId, lastId)
                                .orderByDesc(ShopFavoritePO::getId))
                .getRecords();
        List<FavoriteShopEntity> result = new ArrayList<>();
        for (ShopFavoritePO favorite : favorites) {
            ShopPO shop = shopMapper.selectById(favorite.getShopId());
            if (shop != null && shop.getStatus() != null && shop.getStatus() == 1) {
                result.add(toFavoriteShopEntity(favorite, shop));
            }
        }
        return result;
    }

    private ShopFavoritePO queryByUserAndShop(Long userId, Long shopId) {
        return shopFavoriteMapper.selectOne(new LambdaQueryWrapper<ShopFavoritePO>()
                .eq(ShopFavoritePO::getUserId, userId)
                .eq(ShopFavoritePO::getShopId, shopId));
    }

    private ShopFavoriteEntity toEntity(ShopFavoritePO po) {
        if (po == null) {
            return null;
        }
        ShopFavoriteEntity entity = new ShopFavoriteEntity();
        entity.setId(po.getId());
        entity.setUserId(po.getUserId());
        entity.setShopId(po.getShopId());
        entity.setFavoriteStatus(po.getFavoriteStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private FavoriteShopEntity toFavoriteShopEntity(ShopFavoritePO favorite, ShopPO shop) {
        FavoriteShopEntity entity = new FavoriteShopEntity();
        entity.setFavoriteId(favorite.getId());
        entity.setShopId(shop.getId());
        entity.setShopName(shop.getName());
        entity.setCategoryId(shop.getCategoryId());
        entity.setImages(shop.getImages());
        entity.setArea(shop.getArea());
        entity.setAddress(shop.getAddress());
        entity.setAvgPrice(shop.getAvgPrice());
        entity.setSold(shop.getSold());
        entity.setComments(shop.getComments());
        entity.setScore(shop.getScore());
        entity.setOpenHours(shop.getOpenHours());
        entity.setFavoriteTime(favorite.getUpdateTime() == null ? favorite.getCreateTime() : favorite.getUpdateTime());
        return entity;
    }
}

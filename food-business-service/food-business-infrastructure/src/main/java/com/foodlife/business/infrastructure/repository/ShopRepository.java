package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodlife.business.domain.shop.model.ShopCategoryEntity;
import com.foodlife.business.domain.shop.model.ShopEntity;
import com.foodlife.business.domain.shop.repository.IShopRepository;
import com.foodlife.business.infrastructure.dao.IShopCategoryMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.ShopCategoryPO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ShopRepository implements IShopRepository {

    private final IShopCategoryMapper shopCategoryMapper;
    private final IShopMapper shopMapper;

    public ShopRepository(IShopCategoryMapper shopCategoryMapper, IShopMapper shopMapper) {
        this.shopCategoryMapper = shopCategoryMapper;
        this.shopMapper = shopMapper;
    }

    @Override
    public List<ShopCategoryEntity> listCategories() {
        return shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategoryPO>()
                        .orderByAsc(ShopCategoryPO::getSort))
                .stream()
                .map(this::toCategoryEntity)
                .collect(Collectors.toList());
    }

    @Override
    public ShopEntity findShopById(Long id) {
        return toShopEntity(shopMapper.selectById(id));
    }

    @Override
    public List<ShopEntity> listShopsByCategory(Long categoryId, Integer current, Integer size) {
        Page<ShopPO> page = shopMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ShopPO>()
                        .eq(ShopPO::getCategoryId, categoryId)
                        .eq(ShopPO::getStatus, 1)
                        .orderByDesc(ShopPO::getSold));
        return page.getRecords().stream().map(this::toShopEntity).collect(Collectors.toList());
    }

    @Override
    public List<ShopEntity> listShopsByName(String name, Integer current, Integer size) {
        Page<ShopPO> page = shopMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ShopPO>()
                        .like(StringUtils.hasText(name), ShopPO::getName, name)
                        .eq(ShopPO::getStatus, 1)
                        .orderByDesc(ShopPO::getSold));
        return page.getRecords().stream().map(this::toShopEntity).collect(Collectors.toList());
    }

    private ShopCategoryEntity toCategoryEntity(ShopCategoryPO po) {
        ShopCategoryEntity entity = new ShopCategoryEntity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setIcon(po.getIcon());
        entity.setSort(po.getSort());
        return entity;
    }

    private ShopEntity toShopEntity(ShopPO po) {
        if (po == null) {
            return null;
        }
        ShopEntity entity = new ShopEntity();
        entity.setId(po.getId());
        entity.setName(po.getName());
        entity.setCategoryId(po.getCategoryId());
        entity.setImages(po.getImages());
        entity.setArea(po.getArea());
        entity.setAddress(po.getAddress());
        entity.setLongitude(po.getLongitude());
        entity.setLatitude(po.getLatitude());
        entity.setAvgPrice(po.getAvgPrice());
        entity.setSold(po.getSold());
        entity.setComments(po.getComments());
        entity.setScore(po.getScore());
        entity.setOpenHours(po.getOpenHours());
        return entity;
    }
}

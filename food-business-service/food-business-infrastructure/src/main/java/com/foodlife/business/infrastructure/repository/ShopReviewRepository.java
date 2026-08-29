package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodlife.business.domain.review.model.ShopReviewEntity;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.IShopReviewMapper;
import com.foodlife.business.infrastructure.dao.po.ShopReviewPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ShopReviewRepository implements IShopReviewRepository {

    private final IShopReviewMapper shopReviewMapper;
    private final IShopMapper shopMapper;

    public ShopReviewRepository(IShopReviewMapper shopReviewMapper, IShopMapper shopMapper) {
        this.shopReviewMapper = shopReviewMapper;
        this.shopMapper = shopMapper;
    }

    @Override
    public boolean existsByOrderIdAndUserId(Long orderId, Long userId) {
        Long count = shopReviewMapper.selectCount(new LambdaQueryWrapper<ShopReviewPO>()
                .eq(ShopReviewPO::getOrderId, orderId)
                .eq(ShopReviewPO::getUserId, userId)
                .eq(ShopReviewPO::getReviewStatus, 1));
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopReviewEntity saveReview(ShopReviewEntity review) {
        ShopReviewPO po = toPO(review);
        try {
            shopReviewMapper.insert(po);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("order already reviewed", e);
        }
        int updated = shopMapper.increaseReviewStats(po.getShopId(), po.getScore());
        if (updated != 1) {
            throw new IllegalArgumentException("shop not found");
        }
        return toEntity(shopReviewMapper.selectById(po.getId()));
    }

    @Override
    public List<ShopReviewEntity> listByShopId(Long shopId, Long lastId, Integer limit) {
        return shopReviewMapper.selectPage(new Page<>(1, limit),
                        new LambdaQueryWrapper<ShopReviewPO>()
                                .eq(ShopReviewPO::getShopId, shopId)
                                .eq(ShopReviewPO::getReviewStatus, 1)
                                .lt(lastId != null, ShopReviewPO::getId, lastId)
                                .orderByDesc(ShopReviewPO::getId))
                .getRecords()
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<ShopReviewEntity> listByUserId(Long userId, Long lastId, Integer limit) {
        return shopReviewMapper.selectPage(new Page<>(1, limit),
                        new LambdaQueryWrapper<ShopReviewPO>()
                                .eq(ShopReviewPO::getUserId, userId)
                                .eq(ShopReviewPO::getReviewStatus, 1)
                                .lt(lastId != null, ShopReviewPO::getId, lastId)
                                .orderByDesc(ShopReviewPO::getId))
                .getRecords()
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private ShopReviewPO toPO(ShopReviewEntity entity) {
        ShopReviewPO po = new ShopReviewPO();
        po.setId(entity.getId());
        po.setReviewNo(entity.getReviewNo());
        po.setUserId(entity.getUserId());
        po.setShopId(entity.getShopId());
        po.setPackageId(entity.getPackageId());
        po.setOrderId(entity.getOrderId());
        po.setOrderNo(entity.getOrderNo());
        po.setScore(entity.getScore());
        po.setContent(entity.getContent());
        po.setImages(entity.getImages());
        po.setReviewStatus(entity.getReviewStatus());
        po.setCreateTime(entity.getCreateTime());
        po.setUpdateTime(entity.getUpdateTime());
        return po;
    }

    private ShopReviewEntity toEntity(ShopReviewPO po) {
        if (po == null) {
            return null;
        }
        ShopReviewEntity entity = new ShopReviewEntity();
        entity.setId(po.getId());
        entity.setReviewNo(po.getReviewNo());
        entity.setUserId(po.getUserId());
        entity.setShopId(po.getShopId());
        entity.setPackageId(po.getPackageId());
        entity.setOrderId(po.getOrderId());
        entity.setOrderNo(po.getOrderNo());
        entity.setScore(po.getScore());
        entity.setContent(po.getContent());
        entity.setImages(po.getImages());
        entity.setReviewStatus(po.getReviewStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }
}

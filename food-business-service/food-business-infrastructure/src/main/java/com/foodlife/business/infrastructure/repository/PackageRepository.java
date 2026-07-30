package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import com.foodlife.business.infrastructure.dao.IMealPackageMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.MealPackagePO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PackageRepository implements IPackageRepository {

    private final IMealPackageMapper mealPackageMapper;
    private final IShopMapper shopMapper;

    public PackageRepository(IMealPackageMapper mealPackageMapper, IShopMapper shopMapper) {
        this.mealPackageMapper = mealPackageMapper;
        this.shopMapper = shopMapper;
    }

    @Override
    public MealPackageEntity findById(Long id) {
        return toEntity(mealPackageMapper.selectById(id));
    }

    @Override
    public List<MealPackageEntity> listByShopId(Long shopId) {
        return mealPackageMapper.selectList(new LambdaQueryWrapper<MealPackagePO>()
                        .eq(MealPackagePO::getShopId, shopId)
                        .eq(MealPackagePO::getStatus, 1)
                        .orderByDesc(MealPackagePO::getSold))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PackageTradeSnapshotEntity queryTradeSnapshot(Long packageId) {
        MealPackagePO packagePO = mealPackageMapper.selectById(packageId);
        if (packagePO == null) {
            return null;
        }
        ShopPO shopPO = shopMapper.selectById(packagePO.getShopId());
        if (shopPO == null) {
            return null;
        }
        PackageTradeSnapshotEntity snapshot = new PackageTradeSnapshotEntity();
        snapshot.setShopId(shopPO.getId());
        snapshot.setShopName(shopPO.getName());
        snapshot.setPackageId(packagePO.getId());
        snapshot.setPackageName(packagePO.getName());
        snapshot.setPackageDescription(packagePO.getDescription());
        snapshot.setCoverImage(packagePO.getCoverImage());
        snapshot.setPrice(packagePO.getPrice());
        snapshot.setOriginalPrice(packagePO.getOriginalPrice());
        snapshot.setStock(packagePO.getStock());
        snapshot.setPackageStatus(packagePO.getStatus());
        snapshot.setUseRule(packagePO.getUseRule());
        return snapshot;
    }

    private MealPackageEntity toEntity(MealPackagePO po) {
        if (po == null) {
            return null;
        }
        MealPackageEntity entity = new MealPackageEntity();
        entity.setId(po.getId());
        entity.setShopId(po.getShopId());
        entity.setName(po.getName());
        entity.setDescription(po.getDescription());
        entity.setCoverImage(po.getCoverImage());
        entity.setPrice(po.getPrice());
        entity.setOriginalPrice(po.getOriginalPrice());
        entity.setStock(po.getStock());
        entity.setSold(po.getSold());
        entity.setStatus(po.getStatus());
        entity.setUseRule(po.getUseRule());
        return entity;
    }
}

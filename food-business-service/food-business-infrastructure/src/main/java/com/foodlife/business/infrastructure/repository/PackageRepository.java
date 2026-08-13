package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import com.foodlife.business.infrastructure.dao.IMealPackageMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.MealPackagePO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity) {
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("stock = stock - " + quantity)
                .eq(MealPackagePO::getId, packageId)
                .eq(MealPackagePO::getStatus, 1)
                .ge(MealPackagePO::getStock, quantity));
        if (updated <= 0) {
            throw new IllegalArgumentException("package stock not enough");
        }
        return buildStockChangeResult(packageId, quantity, "OCCUPY");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity) {
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("stock = stock + " + quantity)
                .eq(MealPackagePO::getId, packageId));
        if (updated <= 0) {
            throw new IllegalArgumentException("package not found");
        }
        return buildStockChangeResult(packageId, quantity, "RELEASE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity) {
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("sold = sold + " + quantity)
                .eq(MealPackagePO::getId, packageId));
        if (updated <= 0) {
            throw new IllegalArgumentException("package not found");
        }
        updateShopSold(packageId, quantity);
        return buildStockChangeResult(packageId, quantity, "CONFIRM_SOLD");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity) {
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("sold = sold - " + quantity)
                .eq(MealPackagePO::getId, packageId)
                .ge(MealPackagePO::getSold, quantity));
        if (updated <= 0) {
            throw new IllegalArgumentException("package sold can not rollback");
        }
        updateShopSold(packageId, -quantity);
        return buildStockChangeResult(packageId, quantity, "ROLLBACK_SOLD");
    }

    private void updateShopSold(Long packageId, Integer quantityDelta) {
        MealPackagePO packagePO = mealPackageMapper.selectById(packageId);
        if (packagePO == null) {
            throw new IllegalArgumentException("package not found");
        }
        String soldChangeSql = quantityDelta >= 0
                ? "sold = sold + " + quantityDelta
                : "sold = sold - " + (-quantityDelta);
        LambdaUpdateWrapper<ShopPO> wrapper = new LambdaUpdateWrapper<ShopPO>()
                .setSql(soldChangeSql)
                .eq(ShopPO::getId, packagePO.getShopId());
        if (quantityDelta < 0) {
            wrapper.ge(ShopPO::getSold, -quantityDelta);
        }
        int updated = shopMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new IllegalArgumentException("shop sold can not update");
        }
    }

    private PackageStockChangeResult buildStockChangeResult(Long packageId, Integer quantity, String changeType) {
        MealPackagePO packagePO = mealPackageMapper.selectById(packageId);
        PackageStockChangeResult result = new PackageStockChangeResult();
        result.setPackageId(packageId);
        result.setQuantity(quantity);
        result.setChangeType(changeType);
        if (packagePO != null) {
            result.setStock(packagePO.getStock());
            result.setSold(packagePO.getSold());
        }
        return result;
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

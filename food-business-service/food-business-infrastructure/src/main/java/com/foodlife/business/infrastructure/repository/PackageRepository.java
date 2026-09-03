package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import com.foodlife.business.infrastructure.dao.IMealPackageMapper;
import com.foodlife.business.infrastructure.dao.IPackageStockChangeRecordMapper;
import com.foodlife.business.infrastructure.dao.IShopMapper;
import com.foodlife.business.infrastructure.dao.po.MealPackagePO;
import com.foodlife.business.infrastructure.dao.po.PackageStockChangeRecordPO;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PackageRepository implements IPackageRepository {

    private final IMealPackageMapper mealPackageMapper;
    private final IShopMapper shopMapper;
    private final IPackageStockChangeRecordMapper packageStockChangeRecordMapper;

    public PackageRepository(IMealPackageMapper mealPackageMapper,
                             IShopMapper shopMapper,
                             IPackageStockChangeRecordMapper packageStockChangeRecordMapper) {
        this.mealPackageMapper = mealPackageMapper;
        this.shopMapper = shopMapper;
        this.packageStockChangeRecordMapper = packageStockChangeRecordMapper;
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
        return occupyPackageStock(packageId, quantity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        PackageStockChangeResult handledResult = queryHandledOperationResult(operationId, packageId, quantity, "OCCUPY");
        if (handledResult != null) {
            return handledResult;
        }
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("stock = stock - " + quantity)
                .eq(MealPackagePO::getId, packageId)
                .eq(MealPackagePO::getStatus, 1)
                .ge(MealPackagePO::getStock, quantity));
        if (updated <= 0) {
            throw new IllegalArgumentException("package stock not enough");
        }
        saveHandledOperation(operationId, packageId, quantity, "OCCUPY");
        return buildStockChangeResult(packageId, quantity, "OCCUPY");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity) {
        return releasePackageStock(packageId, quantity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity, String operationId) {
        PackageStockChangeResult handledResult = queryHandledOperationResult(operationId, packageId, quantity, "RELEASE");
        if (handledResult != null) {
            return handledResult;
        }
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("stock = stock + " + quantity)
                .eq(MealPackagePO::getId, packageId));
        if (updated <= 0) {
            throw new IllegalArgumentException("package not found");
        }
        saveHandledOperation(operationId, packageId, quantity, "RELEASE");
        return buildStockChangeResult(packageId, quantity, "RELEASE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity) {
        return confirmPackageSold(packageId, quantity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        PackageStockChangeResult handledResult = queryHandledOperationResult(operationId, packageId, quantity, "CONFIRM_SOLD");
        if (handledResult != null) {
            return handledResult;
        }
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("sold = sold + " + quantity)
                .eq(MealPackagePO::getId, packageId));
        if (updated <= 0) {
            throw new IllegalArgumentException("package not found");
        }
        updateShopSold(packageId, quantity);
        saveHandledOperation(operationId, packageId, quantity, "CONFIRM_SOLD");
        return buildStockChangeResult(packageId, quantity, "CONFIRM_SOLD");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity) {
        return rollbackPackageSold(packageId, quantity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        PackageStockChangeResult handledResult = queryHandledOperationResult(operationId, packageId, quantity, "ROLLBACK_SOLD");
        if (handledResult != null) {
            return handledResult;
        }
        int updated = mealPackageMapper.update(null, new LambdaUpdateWrapper<MealPackagePO>()
                .setSql("sold = sold - " + quantity)
                .eq(MealPackagePO::getId, packageId)
                .ge(MealPackagePO::getSold, quantity));
        if (updated <= 0) {
            throw new IllegalArgumentException("package sold can not rollback");
        }
        updateShopSold(packageId, -quantity);
        saveHandledOperation(operationId, packageId, quantity, "ROLLBACK_SOLD");
        return buildStockChangeResult(packageId, quantity, "ROLLBACK_SOLD");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdjustPackageStockResult adjustPackageStock(AdjustPackageStockCommand command) {
        String changeType = "ADMIN_ADJUST";
        AdjustPackageStockResult handledResult = queryHandledAdjustOperationResult(command, changeType);
        if (handledResult != null) {
            return handledResult;
        }

        Integer adjustQuantity = command.getAdjustQuantity();
        LambdaUpdateWrapper<MealPackagePO> wrapper = new LambdaUpdateWrapper<MealPackagePO>()
                .eq(MealPackagePO::getId, command.getPackageId())
                .setSql(buildStockAdjustSql(adjustQuantity));
        if (adjustQuantity < 0) {
            wrapper.ge(MealPackagePO::getStock, -adjustQuantity);
        }
        int updated = mealPackageMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new IllegalArgumentException(adjustQuantity < 0 ? "package stock not enough" : "package not found");
        }
        saveHandledOperation(command.getOperationId(), command.getPackageId(), adjustQuantity, changeType);
        return buildAdjustPackageStockResult(command, changeType);
    }

    @Override
    public List<PackageStockChangeRecordEntity> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit) {
        LambdaQueryWrapper<PackageStockChangeRecordPO> wrapper = new LambdaQueryWrapper<PackageStockChangeRecordPO>()
                .orderByDesc(PackageStockChangeRecordPO::getId)
                .last("limit " + limit);
        if (!isBlank(operationIdPrefix)) {
            wrapper.likeRight(PackageStockChangeRecordPO::getOperationId, operationIdPrefix);
        }
        if (packageId != null) {
            wrapper.eq(PackageStockChangeRecordPO::getPackageId, packageId);
        }
        return packageStockChangeRecordMapper.selectList(wrapper)
                .stream()
                .map(this::toStockChangeRecordEntity)
                .collect(Collectors.toList());
    }

    private PackageStockChangeResult queryHandledOperationResult(String operationId, Long packageId, Integer quantity, String changeType) {
        if (isBlank(operationId)) {
            return null;
        }
        PackageStockChangeRecordPO record = packageStockChangeRecordMapper.selectOne(new LambdaQueryWrapper<PackageStockChangeRecordPO>()
                .eq(PackageStockChangeRecordPO::getOperationId, operationId)
                .last("limit 1"));
        if (record == null) {
            return null;
        }
        if (!packageId.equals(record.getPackageId())
                || !quantity.equals(record.getQuantity())
                || !changeType.equals(record.getChangeType())) {
            throw new IllegalArgumentException("operationId already used by another stock change");
        }
        return buildStockChangeResult(packageId, quantity, changeType);
    }

    private AdjustPackageStockResult queryHandledAdjustOperationResult(AdjustPackageStockCommand command, String changeType) {
        PackageStockChangeRecordPO record = packageStockChangeRecordMapper.selectOne(new LambdaQueryWrapper<PackageStockChangeRecordPO>()
                .eq(PackageStockChangeRecordPO::getOperationId, command.getOperationId())
                .last("limit 1"));
        if (record == null) {
            return null;
        }
        if (!command.getPackageId().equals(record.getPackageId())
                || !command.getAdjustQuantity().equals(record.getQuantity())
                || !changeType.equals(record.getChangeType())) {
            throw new IllegalArgumentException("operationId already used by another stock change");
        }
        return buildAdjustPackageStockResult(command, changeType);
    }

    private String buildStockAdjustSql(Integer adjustQuantity) {
        if (adjustQuantity > 0) {
            return "stock = stock + " + adjustQuantity;
        }
        return "stock = stock - " + (-adjustQuantity);
    }

    private void saveHandledOperation(String operationId, Long packageId, Integer quantity, String changeType) {
        if (isBlank(operationId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        PackageStockChangeRecordPO record = new PackageStockChangeRecordPO();
        record.setOperationId(operationId);
        record.setPackageId(packageId);
        record.setQuantity(quantity);
        record.setChangeType(changeType);
        record.setChangeStatus("SUCCESS");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        packageStockChangeRecordMapper.insert(record);
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

    private AdjustPackageStockResult buildAdjustPackageStockResult(AdjustPackageStockCommand command, String changeType) {
        MealPackagePO packagePO = mealPackageMapper.selectById(command.getPackageId());
        AdjustPackageStockResult result = new AdjustPackageStockResult();
        result.setPackageId(command.getPackageId());
        result.setOperatorId(command.getOperatorId());
        result.setAdjustQuantity(command.getAdjustQuantity());
        result.setChangeType(changeType);
        result.setOperationId(command.getOperationId());
        if (packagePO != null) {
            result.setStock(packagePO.getStock());
            result.setSold(packagePO.getSold());
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private PackageStockChangeRecordEntity toStockChangeRecordEntity(PackageStockChangeRecordPO po) {
        PackageStockChangeRecordEntity entity = new PackageStockChangeRecordEntity();
        entity.setId(po.getId());
        entity.setOperationId(po.getOperationId());
        entity.setPackageId(po.getPackageId());
        entity.setQuantity(po.getQuantity());
        entity.setChangeType(po.getChangeType());
        entity.setChangeStatus(po.getChangeStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
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

package com.foodlife.business.domain.packagee.repository;

import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;

import java.util.List;

public interface IPackageRepository {

    MealPackageEntity findById(Long id);

    List<MealPackageEntity> listByShopId(Long shopId);

    PackageTradeSnapshotEntity queryTradeSnapshot(Long packageId);

    PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity);

    PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity, String operationId);

    PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity);

    PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity, String operationId);

    PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity);

    PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity, String operationId);

    PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity);

    PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity, String operationId);

    AdjustPackageStockResult adjustPackageStock(AdjustPackageStockCommand command);

    List<PackageStockChangeRecordEntity> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit);
}

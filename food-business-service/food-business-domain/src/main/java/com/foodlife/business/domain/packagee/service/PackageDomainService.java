package com.foodlife.business.domain.packagee.service;

import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PackageDomainService {

    private final IPackageRepository packageRepository;

    public PackageDomainService(IPackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    public MealPackageEntity queryPackageById(Long id) {
        return packageRepository.findById(id);
    }

    public List<MealPackageEntity> queryPackagesByShopId(Long shopId) {
        return packageRepository.listByShopId(shopId);
    }

    public PackageTradeSnapshotEntity queryTradeSnapshot(Long packageId) {
        return packageRepository.queryTradeSnapshot(packageId);
    }

    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity) {
        return occupyPackageStock(packageId, quantity, null);
    }

    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.occupyPackageStock(packageId, quantity, operationId);
    }

    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity) {
        return releasePackageStock(packageId, quantity, null);
    }

    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.releasePackageStock(packageId, quantity, operationId);
    }

    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity) {
        return confirmPackageSold(packageId, quantity, null);
    }

    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.confirmPackageSold(packageId, quantity, operationId);
    }

    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity) {
        return rollbackPackageSold(packageId, quantity, null);
    }

    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.rollbackPackageSold(packageId, quantity, operationId);
    }

    private void checkPackageStockCommand(Long packageId, Integer quantity) {
        if (packageId == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}

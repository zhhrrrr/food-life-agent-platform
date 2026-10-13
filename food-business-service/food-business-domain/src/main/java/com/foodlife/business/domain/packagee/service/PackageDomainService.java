package com.foodlife.business.domain.packagee.service;

import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PackageDomainService {

    private static final int DEFAULT_RECORD_LIMIT = 20;
    private static final int MAX_RECORD_LIMIT = 100;

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

    public List<PackageTradeSnapshotEntity> queryTradeSnapshots(List<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty() || packageIds.size() > 50
                || packageIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("packageIds must contain 1 to 50 positive IDs");
        }
        return packageRepository.queryTradeSnapshots(packageIds.stream().distinct().collect(Collectors.toList()));
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity) {
        return occupyPackageStock(packageId, quantity, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult occupyPackageStock(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.occupyPackageStock(packageId, quantity, operationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity) {
        return releasePackageStock(packageId, quantity, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.releasePackageStock(packageId, quantity, operationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity) {
        return confirmPackageSold(packageId, quantity, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.confirmPackageSold(packageId, quantity, operationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity) {
        return rollbackPackageSold(packageId, quantity, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        return packageRepository.rollbackPackageSold(packageId, quantity, operationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdjustPackageStockResult adjustPackageStock(AdjustPackageStockCommand command) {
        checkAdjustPackageStockCommand(command);
        return packageRepository.adjustPackageStock(command);
    }

    public List<PackageStockChangeRecordEntity> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit) {
        return packageRepository.listStockChangeRecords(trimToNull(operationIdPrefix), packageId, normalizeLimit(limit));
    }

    private void checkPackageStockCommand(Long packageId, Integer quantity) {
        if (packageId == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private void checkAdjustPackageStockCommand(AdjustPackageStockCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("adjust command required");
        }
        if (command.getPackageId() == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (command.getOperatorId() == null) {
            throw new IllegalArgumentException("operatorId required");
        }
        if (command.getAdjustQuantity() == null || command.getAdjustQuantity() == 0) {
            throw new IllegalArgumentException("adjustQuantity must not be zero");
        }
        if (isBlank(command.getOperationId())) {
            throw new IllegalArgumentException("operationId required");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RECORD_LIMIT;
        }
        return Math.min(limit, MAX_RECORD_LIMIT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}

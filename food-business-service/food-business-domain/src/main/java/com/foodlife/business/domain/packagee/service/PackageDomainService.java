package com.foodlife.business.domain.packagee.service;

import com.foodlife.business.domain.event.BusinessMqTopics;
import com.foodlife.business.domain.event.IBusinessEventPublisher;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockCommand;
import com.foodlife.business.domain.packagee.model.AdjustPackageStockResult;
import com.foodlife.business.domain.packagee.model.MealPackageEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeRecordEntity;
import com.foodlife.business.domain.packagee.model.PackageStockChangeResult;
import com.foodlife.business.domain.packagee.model.PackageTradeSnapshotEntity;
import com.foodlife.business.domain.packagee.repository.IPackageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PackageDomainService {

    private static final int DEFAULT_RECORD_LIMIT = 20;
    private static final int MAX_RECORD_LIMIT = 100;

    private final IPackageRepository packageRepository;
    private final IBusinessEventPublisher businessEventPublisher;

    public PackageDomainService(IPackageRepository packageRepository,
                                IBusinessEventPublisher businessEventPublisher) {
        this.packageRepository = packageRepository;
        this.businessEventPublisher = businessEventPublisher;
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
        PackageStockChangeResult result = packageRepository.occupyPackageStock(packageId, quantity, operationId);
        publishStockEvent(BusinessMqTopics.STOCK_OCCUPIED, result, operationId);
        return result;
    }

    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity) {
        return releasePackageStock(packageId, quantity, null);
    }

    public PackageStockChangeResult releasePackageStock(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        PackageStockChangeResult result = packageRepository.releasePackageStock(packageId, quantity, operationId);
        publishStockEvent(BusinessMqTopics.STOCK_RELEASED, result, operationId);
        return result;
    }

    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity) {
        return confirmPackageSold(packageId, quantity, null);
    }

    public PackageStockChangeResult confirmPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        PackageStockChangeResult result = packageRepository.confirmPackageSold(packageId, quantity, operationId);
        publishStockEvent(BusinessMqTopics.STOCK_SOLD_CONFIRMED, result, operationId);
        return result;
    }

    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity) {
        return rollbackPackageSold(packageId, quantity, null);
    }

    public PackageStockChangeResult rollbackPackageSold(Long packageId, Integer quantity, String operationId) {
        checkPackageStockCommand(packageId, quantity);
        PackageStockChangeResult result = packageRepository.rollbackPackageSold(packageId, quantity, operationId);
        publishStockEvent(BusinessMqTopics.STOCK_ROLLBACK, result, operationId);
        return result;
    }

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

    private void publishStockEvent(String tag, PackageStockChangeResult result, String operationId) {
        String key = result.getPackageId() + ":" + tag + ":" + readOrDefault(operationId, String.valueOf(System.currentTimeMillis()));
        businessEventPublisher.publish(BusinessMqTopics.PACKAGE_STOCK_TOPIC, tag, key, result);
    }

    private String readOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}

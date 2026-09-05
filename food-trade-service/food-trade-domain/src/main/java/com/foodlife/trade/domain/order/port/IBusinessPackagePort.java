package com.foodlife.trade.domain.order.port;

import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;
import com.foodlife.trade.domain.order.operation.model.PackageStockAdjustResult;
import com.foodlife.trade.domain.order.normal.model.PackageStockChangeRecord;

import java.util.List;

public interface IBusinessPackagePort {

    PackageTradeSnapshot queryTradeSnapshot(Long packageId);

    List<PackageStockChangeRecord> listStockChangeRecords(String operationIdPrefix, Long packageId, Integer limit);

    void occupyPackageStock(Long packageId, Integer quantity);

    void occupyPackageStock(Long packageId, Integer quantity, String operationId);

    void releasePackageStock(Long packageId, Integer quantity);

    void releasePackageStock(Long packageId, Integer quantity, String operationId);

    void confirmPackageSold(Long packageId, Integer quantity);

    void confirmPackageSold(Long packageId, Integer quantity, String operationId);

    void rollbackPackageSold(Long packageId, Integer quantity);

    void rollbackPackageSold(Long packageId, Integer quantity, String operationId);

    PackageStockAdjustResult adjustPackageStock(Long packageId, Integer adjustQuantity, Long operatorId, String reason, String operationId);
}

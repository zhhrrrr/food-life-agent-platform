package com.foodlife.trade.domain.order.port;

import com.foodlife.trade.domain.order.model.PackageTradeSnapshot;

public interface IBusinessPackagePort {

    PackageTradeSnapshot queryTradeSnapshot(Long packageId);

    void occupyPackageStock(Long packageId, Integer quantity);

    void occupyPackageStock(Long packageId, Integer quantity, String operationId);

    void releasePackageStock(Long packageId, Integer quantity);

    void releasePackageStock(Long packageId, Integer quantity, String operationId);

    void confirmPackageSold(Long packageId, Integer quantity);

    void confirmPackageSold(Long packageId, Integer quantity, String operationId);

    void rollbackPackageSold(Long packageId, Integer quantity);

    void rollbackPackageSold(Long packageId, Integer quantity, String operationId);
}

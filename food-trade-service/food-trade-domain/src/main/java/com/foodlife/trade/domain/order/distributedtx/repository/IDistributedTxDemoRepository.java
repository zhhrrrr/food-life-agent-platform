package com.foodlife.trade.domain.order.distributedtx.repository;

import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustLog;

public interface IDistributedTxDemoRepository {

    DistributedPackageStockAdjustLog findByOperationId(String operationId);

    void saveProcessingLog(DistributedPackageStockAdjustCommand command);

    void markSuccess(String operationId, Integer stock, Integer sold);
}

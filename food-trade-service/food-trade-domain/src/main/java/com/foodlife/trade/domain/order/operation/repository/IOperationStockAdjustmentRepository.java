package com.foodlife.trade.domain.order.operation.repository;

import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustLog;

public interface IOperationStockAdjustmentRepository {

    OperationPackageStockAdjustLog findByOperationId(String operationId);

    void saveProcessingLog(OperationPackageStockAdjustCommand command);

    void markSuccess(String operationId, Integer stock, Integer sold);
}

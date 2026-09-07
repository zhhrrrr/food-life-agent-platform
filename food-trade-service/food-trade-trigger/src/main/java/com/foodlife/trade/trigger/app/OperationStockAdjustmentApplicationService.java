package com.foodlife.trade.trigger.app;

import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustResult;
import com.foodlife.trade.domain.order.operation.service.OperationStockAdjustmentService;
import org.springframework.stereotype.Service;

@Service
public class OperationStockAdjustmentApplicationService {

    private final OperationStockAdjustmentService operationStockAdjustmentService;

    public OperationStockAdjustmentApplicationService(OperationStockAdjustmentService operationStockAdjustmentService) {
        this.operationStockAdjustmentService = operationStockAdjustmentService;
    }

    public OperationPackageStockAdjustResult adjustPackageStock(OperationPackageStockAdjustCommand command) {
        return operationStockAdjustmentService.adjustPackageStock(command);
    }
}

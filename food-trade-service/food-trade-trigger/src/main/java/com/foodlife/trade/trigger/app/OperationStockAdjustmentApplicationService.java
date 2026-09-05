package com.foodlife.trade.trigger.app;

import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustResult;
import com.foodlife.trade.domain.order.operation.service.OperationStockAdjustmentService;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

@Service
public class OperationStockAdjustmentApplicationService {

    private final OperationStockAdjustmentService operationStockAdjustmentService;

    public OperationStockAdjustmentApplicationService(OperationStockAdjustmentService operationStockAdjustmentService) {
        this.operationStockAdjustmentService = operationStockAdjustmentService;
    }

    @GlobalTransactional(name = "food-operation-package-stock-adjust", rollbackFor = Exception.class)
    public OperationPackageStockAdjustResult adjustPackageStock(OperationPackageStockAdjustCommand command) {
        return operationStockAdjustmentService.adjustPackageStock(command);
    }
}

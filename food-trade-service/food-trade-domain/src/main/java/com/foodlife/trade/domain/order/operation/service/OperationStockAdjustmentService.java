package com.foodlife.trade.domain.order.operation.service;

import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustLog;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustResult;
import com.foodlife.trade.domain.order.operation.model.PackageStockAdjustResult;
import com.foodlife.trade.domain.order.operation.repository.IOperationStockAdjustmentRepository;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationStockAdjustmentService {

    private final IBusinessPackagePort businessPackagePort;
    private final IOperationStockAdjustmentRepository operationStockAdjustmentRepository;

    public OperationStockAdjustmentService(IBusinessPackagePort businessPackagePort,
                                           IOperationStockAdjustmentRepository operationStockAdjustmentRepository) {
        this.businessPackagePort = businessPackagePort;
        this.operationStockAdjustmentRepository = operationStockAdjustmentRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public OperationPackageStockAdjustResult adjustPackageStock(OperationPackageStockAdjustCommand command) {
        checkCommand(command);
        fillOperationId(command);
        OperationPackageStockAdjustLog handledLog = operationStockAdjustmentRepository.findByOperationId(command.getOperationId());
        if (handledLog != null) {
            if ("SUCCESS".equals(handledLog.getTxStatus())) {
                return toResult(handledLog);
            }
            throw new IllegalArgumentException("operation is processing");
        }

        operationStockAdjustmentRepository.saveProcessingLog(command);
        PackageStockAdjustResult stockAdjustResult = businessPackagePort.adjustPackageStock(
                command.getPackageId(),
                command.getAdjustQuantity(),
                command.getOperatorId(),
                command.getReason(),
                command.getOperationId()
        );
        operationStockAdjustmentRepository.markSuccess(command.getOperationId(), stockAdjustResult.getStock(), stockAdjustResult.getSold());
        return toResult(command, stockAdjustResult);
    }

    private void checkCommand(OperationPackageStockAdjustCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("adjust command required");
        }
        if (command.getOperatorId() == null) {
            throw new IllegalArgumentException("operatorId required");
        }
        if (command.getPackageId() == null) {
            throw new IllegalArgumentException("packageId required");
        }
        if (command.getAdjustQuantity() == null || command.getAdjustQuantity() == 0) {
            throw new IllegalArgumentException("adjustQuantity must not be zero");
        }
    }

    private void fillOperationId(OperationPackageStockAdjustCommand command) {
        if (command.getOperationId() == null || command.getOperationId().trim().isEmpty()) {
            command.setOperationId("OPERATION_PACKAGE_STOCK_ADJUST:" + command.getOperatorId() + ":"
                    + command.getPackageId() + ":" + System.currentTimeMillis());
        } else {
            command.setOperationId(command.getOperationId().trim());
        }
    }

    private OperationPackageStockAdjustResult toResult(OperationPackageStockAdjustLog log) {
        OperationPackageStockAdjustResult result = new OperationPackageStockAdjustResult();
        result.setOperationId(log.getOperationId());
        result.setOperatorId(log.getOperatorId());
        result.setPackageId(log.getPackageId());
        result.setAdjustQuantity(log.getAdjustQuantity());
        result.setStock(log.getStock());
        result.setSold(log.getSold());
        result.setTxStatus(log.getTxStatus());
        return result;
    }

    private OperationPackageStockAdjustResult toResult(OperationPackageStockAdjustCommand command,
                                                       PackageStockAdjustResult stockAdjustResult) {
        OperationPackageStockAdjustResult result = new OperationPackageStockAdjustResult();
        result.setOperationId(command.getOperationId());
        result.setOperatorId(command.getOperatorId());
        result.setPackageId(command.getPackageId());
        result.setAdjustQuantity(command.getAdjustQuantity());
        result.setStock(stockAdjustResult.getStock());
        result.setSold(stockAdjustResult.getSold());
        result.setTxStatus("SUCCESS");
        return result;
    }
}

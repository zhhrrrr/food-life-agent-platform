package com.foodlife.trade.domain.order.distributedtx.service;

import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustLog;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustResult;
import com.foodlife.trade.domain.order.distributedtx.model.PackageStockAdjustResult;
import com.foodlife.trade.domain.order.distributedtx.repository.IDistributedTxDemoRepository;
import com.foodlife.trade.domain.order.port.IBusinessPackagePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributedTxDemoService {

    private final IBusinessPackagePort businessPackagePort;
    private final IDistributedTxDemoRepository distributedTxDemoRepository;

    public DistributedTxDemoService(IBusinessPackagePort businessPackagePort,
                                    IDistributedTxDemoRepository distributedTxDemoRepository) {
        this.businessPackagePort = businessPackagePort;
        this.distributedTxDemoRepository = distributedTxDemoRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public DistributedPackageStockAdjustResult adjustPackageStock(DistributedPackageStockAdjustCommand command) {
        checkCommand(command);
        fillOperationId(command);
        DistributedPackageStockAdjustLog handledLog = distributedTxDemoRepository.findByOperationId(command.getOperationId());
        if (handledLog != null) {
            if ("SUCCESS".equals(handledLog.getTxStatus())) {
                return toResult(handledLog);
            }
            throw new IllegalArgumentException("operation is processing");
        }

        distributedTxDemoRepository.saveProcessingLog(command);
        PackageStockAdjustResult stockAdjustResult = businessPackagePort.adjustPackageStock(
                command.getPackageId(),
                command.getAdjustQuantity(),
                command.getOperatorId(),
                command.getReason(),
                command.getOperationId()
        );
        distributedTxDemoRepository.markSuccess(command.getOperationId(), stockAdjustResult.getStock(), stockAdjustResult.getSold());
        return toResult(command, stockAdjustResult);
    }

    private void checkCommand(DistributedPackageStockAdjustCommand command) {
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

    private void fillOperationId(DistributedPackageStockAdjustCommand command) {
        if (command.getOperationId() == null || command.getOperationId().trim().isEmpty()) {
            command.setOperationId("SEATA_PACKAGE_STOCK_ADJUST:" + command.getOperatorId() + ":"
                    + command.getPackageId() + ":" + System.currentTimeMillis());
        } else {
            command.setOperationId(command.getOperationId().trim());
        }
    }

    private DistributedPackageStockAdjustResult toResult(DistributedPackageStockAdjustLog log) {
        DistributedPackageStockAdjustResult result = new DistributedPackageStockAdjustResult();
        result.setOperationId(log.getOperationId());
        result.setOperatorId(log.getOperatorId());
        result.setPackageId(log.getPackageId());
        result.setAdjustQuantity(log.getAdjustQuantity());
        result.setStock(log.getStock());
        result.setSold(log.getSold());
        result.setTxStatus(log.getTxStatus());
        return result;
    }

    private DistributedPackageStockAdjustResult toResult(DistributedPackageStockAdjustCommand command,
                                                        PackageStockAdjustResult stockAdjustResult) {
        DistributedPackageStockAdjustResult result = new DistributedPackageStockAdjustResult();
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

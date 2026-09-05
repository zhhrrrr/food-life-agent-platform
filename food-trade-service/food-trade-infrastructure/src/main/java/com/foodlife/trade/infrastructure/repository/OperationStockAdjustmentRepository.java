package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.operation.model.OperationPackageStockAdjustLog;
import com.foodlife.trade.domain.order.operation.repository.IOperationStockAdjustmentRepository;
import com.foodlife.trade.infrastructure.dao.IOperationStockAdjustLogMapper;
import com.foodlife.trade.infrastructure.dao.po.OperationStockAdjustLogPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class OperationStockAdjustmentRepository implements IOperationStockAdjustmentRepository {

    private final IOperationStockAdjustLogMapper operationStockAdjustLogMapper;

    public OperationStockAdjustmentRepository(IOperationStockAdjustLogMapper operationStockAdjustLogMapper) {
        this.operationStockAdjustLogMapper = operationStockAdjustLogMapper;
    }

    @Override
    public OperationPackageStockAdjustLog findByOperationId(String operationId) {
        OperationStockAdjustLogPO po = operationStockAdjustLogMapper.selectOne(new LambdaQueryWrapper<OperationStockAdjustLogPO>()
                .eq(OperationStockAdjustLogPO::getOperationId, operationId)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public void saveProcessingLog(OperationPackageStockAdjustCommand command) {
        LocalDateTime now = LocalDateTime.now();
        OperationStockAdjustLogPO po = new OperationStockAdjustLogPO();
        po.setOperationId(command.getOperationId());
        po.setOperatorId(command.getOperatorId());
        po.setPackageId(command.getPackageId());
        po.setAdjustQuantity(command.getAdjustQuantity());
        po.setReason(command.getReason());
        po.setTxStatus("PROCESSING");
        po.setCreateTime(now);
        po.setUpdateTime(now);
        operationStockAdjustLogMapper.insert(po);
    }

    @Override
    public void markSuccess(String operationId, Integer stock, Integer sold) {
        operationStockAdjustLogMapper.update(null, new LambdaUpdateWrapper<OperationStockAdjustLogPO>()
                .eq(OperationStockAdjustLogPO::getOperationId, operationId)
                .set(OperationStockAdjustLogPO::getStock, stock)
                .set(OperationStockAdjustLogPO::getSold, sold)
                .set(OperationStockAdjustLogPO::getTxStatus, "SUCCESS")
                .set(OperationStockAdjustLogPO::getUpdateTime, LocalDateTime.now()));
    }

    private OperationPackageStockAdjustLog toEntity(OperationStockAdjustLogPO po) {
        if (po == null) {
            return null;
        }
        OperationPackageStockAdjustLog log = new OperationPackageStockAdjustLog();
        log.setOperationId(po.getOperationId());
        log.setOperatorId(po.getOperatorId());
        log.setPackageId(po.getPackageId());
        log.setAdjustQuantity(po.getAdjustQuantity());
        log.setStock(po.getStock());
        log.setSold(po.getSold());
        log.setTxStatus(po.getTxStatus());
        return log;
    }
}

package com.foodlife.trade.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustCommand;
import com.foodlife.trade.domain.order.distributedtx.model.DistributedPackageStockAdjustLog;
import com.foodlife.trade.domain.order.distributedtx.repository.IDistributedTxDemoRepository;
import com.foodlife.trade.infrastructure.dao.IDistributedTxDemoLogMapper;
import com.foodlife.trade.infrastructure.dao.po.DistributedTxDemoLogPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class DistributedTxDemoRepository implements IDistributedTxDemoRepository {

    private final IDistributedTxDemoLogMapper distributedTxDemoLogMapper;

    public DistributedTxDemoRepository(IDistributedTxDemoLogMapper distributedTxDemoLogMapper) {
        this.distributedTxDemoLogMapper = distributedTxDemoLogMapper;
    }

    @Override
    public DistributedPackageStockAdjustLog findByOperationId(String operationId) {
        DistributedTxDemoLogPO po = distributedTxDemoLogMapper.selectOne(new LambdaQueryWrapper<DistributedTxDemoLogPO>()
                .eq(DistributedTxDemoLogPO::getOperationId, operationId)
                .last("limit 1"));
        return toEntity(po);
    }

    @Override
    public void saveProcessingLog(DistributedPackageStockAdjustCommand command) {
        LocalDateTime now = LocalDateTime.now();
        DistributedTxDemoLogPO po = new DistributedTxDemoLogPO();
        po.setOperationId(command.getOperationId());
        po.setOperatorId(command.getOperatorId());
        po.setPackageId(command.getPackageId());
        po.setAdjustQuantity(command.getAdjustQuantity());
        po.setReason(command.getReason());
        po.setTxStatus("PROCESSING");
        po.setCreateTime(now);
        po.setUpdateTime(now);
        distributedTxDemoLogMapper.insert(po);
    }

    @Override
    public void markSuccess(String operationId, Integer stock, Integer sold) {
        distributedTxDemoLogMapper.update(null, new LambdaUpdateWrapper<DistributedTxDemoLogPO>()
                .eq(DistributedTxDemoLogPO::getOperationId, operationId)
                .set(DistributedTxDemoLogPO::getStock, stock)
                .set(DistributedTxDemoLogPO::getSold, sold)
                .set(DistributedTxDemoLogPO::getTxStatus, "SUCCESS")
                .set(DistributedTxDemoLogPO::getUpdateTime, LocalDateTime.now()));
    }

    private DistributedPackageStockAdjustLog toEntity(DistributedTxDemoLogPO po) {
        if (po == null) {
            return null;
        }
        DistributedPackageStockAdjustLog log = new DistributedPackageStockAdjustLog();
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

package com.foodlife.trade.domain.order.audit.service;

import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditQueryEntity;
import com.foodlife.trade.domain.order.audit.repository.IOperationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class OperationAuditDomainService {

    private final IOperationAuditRepository operationAuditRepository;

    public OperationAuditDomainService(IOperationAuditRepository operationAuditRepository) {
        this.operationAuditRepository = operationAuditRepository;
    }

    public OperationAuditLogEntity record(OperationAuditCommandEntity command) {
        validate(command);
        return operationAuditRepository.save(command);
    }

    public List<OperationAuditLogEntity> list(OperationAuditQueryEntity query) {
        OperationAuditQueryEntity safeQuery = query == null ? new OperationAuditQueryEntity() : query;
        if (safeQuery.getLimit() == null || safeQuery.getLimit() <= 0 || safeQuery.getLimit() > 200) {
            safeQuery.setLimit(50);
        }
        return operationAuditRepository.list(safeQuery);
    }

    private void validate(OperationAuditCommandEntity command) {
        if (command == null) {
            throw new IllegalArgumentException("audit command required");
        }
        if (!StringUtils.hasText(command.getOperationType())) {
            throw new IllegalArgumentException("operationType required");
        }
        if (!StringUtils.hasText(command.getBizType())) {
            throw new IllegalArgumentException("bizType required");
        }
        if (!StringUtils.hasText(command.getBizId())) {
            throw new IllegalArgumentException("bizId required");
        }
    }
}

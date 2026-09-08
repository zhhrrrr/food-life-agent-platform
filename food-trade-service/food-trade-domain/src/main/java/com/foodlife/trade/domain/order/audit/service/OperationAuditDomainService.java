package com.foodlife.trade.domain.order.audit.service;

import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;
import com.foodlife.trade.domain.order.audit.repository.IOperationAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

package com.foodlife.trade.domain.order.audit.repository;

import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;

public interface IOperationAuditRepository {

    OperationAuditLogEntity save(OperationAuditCommandEntity command);
}

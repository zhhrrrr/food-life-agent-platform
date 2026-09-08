package com.foodlife.trade.domain.order.audit.repository;

import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditQueryEntity;

import java.util.List;

public interface IOperationAuditRepository {

    OperationAuditLogEntity save(OperationAuditCommandEntity command);

    List<OperationAuditLogEntity> list(OperationAuditQueryEntity query);
}

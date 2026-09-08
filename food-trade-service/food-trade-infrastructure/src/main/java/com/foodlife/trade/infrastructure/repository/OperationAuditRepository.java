package com.foodlife.trade.infrastructure.repository;

import com.foodlife.trade.domain.order.audit.model.OperationAuditCommandEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditLogEntity;
import com.foodlife.trade.domain.order.audit.model.OperationAuditQueryEntity;
import com.foodlife.trade.domain.order.audit.repository.IOperationAuditRepository;
import com.foodlife.trade.infrastructure.dao.IOperationAuditLogMapper;
import com.foodlife.trade.infrastructure.dao.po.OperationAuditLogPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OperationAuditRepository implements IOperationAuditRepository {

    private final IOperationAuditLogMapper operationAuditLogMapper;

    public OperationAuditRepository(IOperationAuditLogMapper operationAuditLogMapper) {
        this.operationAuditLogMapper = operationAuditLogMapper;
    }

    @Override
    public OperationAuditLogEntity save(OperationAuditCommandEntity command) {
        OperationAuditLogPO po = new OperationAuditLogPO();
        po.setTraceId(command.getTraceId());
        po.setOperatorId(command.getOperatorId());
        po.setOperatorRole(command.getOperatorRole());
        po.setOperationType(command.getOperationType());
        po.setBizType(command.getBizType());
        po.setBizId(command.getBizId());
        po.setOperationStatus(command.getOperationStatus());
        po.setRequestContent(command.getRequestContent());
        po.setResponseContent(command.getResponseContent());
        po.setRemark(command.getRemark());
        po.setCreateTime(LocalDateTime.now());
        operationAuditLogMapper.insert(po);
        return toEntity(po);
    }

    @Override
    public List<OperationAuditLogEntity> list(OperationAuditQueryEntity query) {
        LambdaQueryWrapper<OperationAuditLogPO> wrapper = new LambdaQueryWrapper<OperationAuditLogPO>()
                .eq(StringUtils.hasText(query.getTraceId()), OperationAuditLogPO::getTraceId, query.getTraceId())
                .eq(query.getOperatorId() != null, OperationAuditLogPO::getOperatorId, query.getOperatorId())
                .eq(StringUtils.hasText(query.getOperationType()), OperationAuditLogPO::getOperationType, query.getOperationType())
                .eq(StringUtils.hasText(query.getBizType()), OperationAuditLogPO::getBizType, query.getBizType())
                .eq(StringUtils.hasText(query.getBizId()), OperationAuditLogPO::getBizId, query.getBizId())
                .orderByDesc(OperationAuditLogPO::getId)
                .last("limit " + query.getLimit());
        return operationAuditLogMapper.selectList(wrapper).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    private OperationAuditLogEntity toEntity(OperationAuditLogPO po) {
        OperationAuditLogEntity entity = new OperationAuditLogEntity();
        entity.setId(po.getId());
        entity.setTraceId(po.getTraceId());
        entity.setOperatorId(po.getOperatorId());
        entity.setOperatorRole(po.getOperatorRole());
        entity.setOperationType(po.getOperationType());
        entity.setBizType(po.getBizType());
        entity.setBizId(po.getBizId());
        entity.setOperationStatus(po.getOperationStatus());
        entity.setRequestContent(po.getRequestContent());
        entity.setResponseContent(po.getResponseContent());
        entity.setRemark(po.getRemark());
        entity.setCreateTime(po.getCreateTime());
        return entity;
    }
}

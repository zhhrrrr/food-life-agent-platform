package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.OperationAuditLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IOperationAuditLogMapper extends BaseMapper<OperationAuditLogPO> {
}

package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.OperationStockAdjustLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IOperationStockAdjustLogMapper extends BaseMapper<OperationStockAdjustLogPO> {
}

package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.DiningOrderPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IDiningOrderMapper extends BaseMapper<DiningOrderPO> {
}

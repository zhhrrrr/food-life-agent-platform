package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.SeckillOrderRequestPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ISeckillOrderRequestMapper extends BaseMapper<SeckillOrderRequestPO> {
}

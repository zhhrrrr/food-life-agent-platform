package com.foodlife.business.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.business.infrastructure.dao.po.BusinessLocalMessagePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IBusinessLocalMessageMapper extends BaseMapper<BusinessLocalMessagePO> {
}

package com.foodlife.trade.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.trade.infrastructure.dao.po.CouponTemplatePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ICouponTemplateMapper extends BaseMapper<CouponTemplatePO> {
}

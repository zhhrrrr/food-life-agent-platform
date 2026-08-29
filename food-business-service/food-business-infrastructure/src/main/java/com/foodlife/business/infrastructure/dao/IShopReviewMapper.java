package com.foodlife.business.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.business.infrastructure.dao.po.ShopReviewPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IShopReviewMapper extends BaseMapper<ShopReviewPO> {
}

package com.foodlife.business.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.business.infrastructure.dao.po.ShopPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IShopMapper extends BaseMapper<ShopPO> {

    @Update("UPDATE shop SET score = FLOOR((score * comments + #{score} * 10) / (comments + 1)), comments = comments + 1 WHERE id = #{shopId}")
    int increaseReviewStats(@Param("shopId") Long shopId, @Param("score") Integer score);
}

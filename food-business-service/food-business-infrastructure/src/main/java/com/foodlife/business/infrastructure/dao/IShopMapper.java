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

    @Update("UPDATE shop SET score = CASE WHEN comments <= 1 THEN 0 ELSE FLOOR(GREATEST(score * comments - #{score} * 10, 0) / (comments - 1)) END, comments = CASE WHEN comments <= 0 THEN 0 ELSE comments - 1 END WHERE id = #{shopId}")
    int decreaseReviewStats(@Param("shopId") Long shopId, @Param("score") Integer score);

    @Update("UPDATE shop SET comments = #{comments}, score = #{score} WHERE id = #{shopId}")
    int restoreReviewStats(@Param("shopId") Long shopId, @Param("comments") Integer comments, @Param("score") Integer score);
}

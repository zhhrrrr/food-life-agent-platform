package com.foodlife.user.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.user.infrastructure.dao.po.UserProfilePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IUserProfileMapper extends BaseMapper<UserProfilePO> {
}

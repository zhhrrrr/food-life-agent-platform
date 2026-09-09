package com.foodlife.user.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodlife.user.infrastructure.dao.po.UserRolePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IUserRoleMapper extends BaseMapper<UserRolePO> {
}


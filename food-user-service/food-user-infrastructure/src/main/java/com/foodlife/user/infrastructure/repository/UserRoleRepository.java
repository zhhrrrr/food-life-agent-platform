package com.foodlife.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.user.domain.user.repository.IUserRoleRepository;
import com.foodlife.user.infrastructure.dao.IUserRoleMapper;
import com.foodlife.user.infrastructure.dao.po.UserRolePO;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

@Repository
public class UserRoleRepository implements IUserRoleRepository {

    private final IUserRoleMapper userRoleMapper;

    public UserRoleRepository(IUserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public String findHighestRoleCodeByUserId(Long userId) {
        if (userId == null) {
            return "USER";
        }
        List<UserRolePO> roles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRolePO>()
                .eq(UserRolePO::getUserId, userId)
                .eq(UserRolePO::getStatus, 1));
        return roles.stream()
                .map(UserRolePO::getRoleCode)
                .filter(role -> role != null && !role.trim().isEmpty())
                .max(Comparator.comparingInt(this::roleWeight))
                .orElse("USER");
    }

    private int roleWeight(String roleCode) {
        String role = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if ("ADMIN".equals(role)) {
            return 30;
        }
        if ("OPERATOR".equals(role)) {
            return 20;
        }
        return 10;
    }
}


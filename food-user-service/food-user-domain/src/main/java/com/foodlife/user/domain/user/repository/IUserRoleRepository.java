package com.foodlife.user.domain.user.repository;

public interface IUserRoleRepository {

    String findHighestRoleCodeByUserId(Long userId);
}


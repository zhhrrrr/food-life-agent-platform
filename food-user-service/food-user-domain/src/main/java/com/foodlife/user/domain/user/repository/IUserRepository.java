package com.foodlife.user.domain.user.repository;

import com.foodlife.user.domain.user.model.UserEntity;

public interface IUserRepository {

    UserEntity findByPhone(String phone);

    UserEntity save(UserEntity user);
}

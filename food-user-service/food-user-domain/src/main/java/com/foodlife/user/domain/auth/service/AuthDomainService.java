package com.foodlife.user.domain.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.foodlife.user.domain.user.model.UserEntity;
import com.foodlife.user.types.constants.UserRedisConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthDomainService {

    public boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

    public UserEntity createUserWithPhone(String phone) {
        UserEntity user = new UserEntity();
        user.setPhone(phone);
        user.setNickName(UserRedisConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setIcon("");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }
}

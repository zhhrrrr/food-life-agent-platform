package com.foodlife.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodlife.user.domain.user.model.UserEntity;
import com.foodlife.user.domain.user.repository.IUserRepository;
import com.foodlife.user.infrastructure.dao.IUserMapper;
import com.foodlife.user.infrastructure.dao.po.UserPO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@ConditionalOnProperty(prefix = "food.user.repository", name = "type", havingValue = "mysql", matchIfMissing = true)
public class MySqlUserRepository implements IUserRepository {

    private final IUserMapper userMapper;

    public MySqlUserRepository(IUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserEntity findByPhone(String phone) {
        UserPO userPO = userMapper.selectOne(new LambdaQueryWrapper<UserPO>()
                .eq(UserPO::getPhone, phone)
                .last("limit 1"));
        return toEntity(userPO);
    }

    @Override
    public UserEntity save(UserEntity user) {
        UserPO userPO = toPO(user);
        userMapper.insert(userPO);
        user.setId(userPO.getId());
        return user;
    }

    private UserEntity toEntity(UserPO userPO) {
        if (userPO == null) {
            return null;
        }
        UserEntity user = new UserEntity();
        user.setId(userPO.getId());
        user.setPhone(userPO.getPhone());
        user.setNickName(userPO.getNickName());
        user.setIcon(userPO.getIcon());
        user.setCreateTime(userPO.getCreateTime());
        user.setUpdateTime(userPO.getUpdateTime());
        return user;
    }

    private UserPO toPO(UserEntity user) {
        UserPO userPO = new UserPO();
        userPO.setId(user.getId());
        userPO.setPhone(user.getPhone());
        userPO.setNickName(user.getNickName());
        userPO.setIcon(user.getIcon() == null ? "" : user.getIcon());
        userPO.setStatus(1);
        userPO.setCreateTime(user.getCreateTime() == null ? LocalDateTime.now() : user.getCreateTime());
        userPO.setUpdateTime(LocalDateTime.now());
        return userPO;
    }
}

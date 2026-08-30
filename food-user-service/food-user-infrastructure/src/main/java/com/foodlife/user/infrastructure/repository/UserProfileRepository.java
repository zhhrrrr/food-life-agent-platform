package com.foodlife.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodlife.user.domain.profile.model.UserProfileEntity;
import com.foodlife.user.domain.profile.model.UserProfileUpdateCommand;
import com.foodlife.user.domain.profile.repository.IUserProfileRepository;
import com.foodlife.user.infrastructure.dao.IUserMapper;
import com.foodlife.user.infrastructure.dao.IUserProfileMapper;
import com.foodlife.user.infrastructure.dao.po.UserPO;
import com.foodlife.user.infrastructure.dao.po.UserProfilePO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserProfileRepository implements IUserProfileRepository {

    private final IUserMapper userMapper;
    private final IUserProfileMapper userProfileMapper;

    public UserProfileRepository(IUserMapper userMapper, IUserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public UserProfileEntity findProfileByUserId(Long userId) {
        UserPO user = userMapper.selectById(userId);
        if (!isNormalUser(user)) {
            return null;
        }
        UserProfilePO profile = queryByUserId(userId);
        return toEntity(user, profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileEntity updateProfile(UserProfileUpdateCommand command) {
        UserPO user = userMapper.selectById(command.getUserId());
        if (!isNormalUser(user)) {
            return null;
        }

        userMapper.update(null, new LambdaUpdateWrapper<UserPO>()
                .set(UserPO::getNickName, trimToEmpty(command.getNickName()))
                .set(UserPO::getIcon, trimToEmpty(command.getIcon()))
                .eq(UserPO::getId, command.getUserId()));

        UserProfilePO profile = queryByUserId(command.getUserId());
        if (profile == null) {
            profile = new UserProfilePO();
            profile.setUserId(command.getUserId());
            profile.setCity(trimToEmpty(command.getCity()));
            profile.setBio(trimToEmpty(command.getBio()));
            profile.setFoodPreference(trimToEmpty(command.getFoodPreference()));
            try {
                userProfileMapper.insert(profile);
            } catch (DuplicateKeyException e) {
                updateProfileFields(command);
            }
        } else {
            updateProfileFields(command);
        }

        return findProfileByUserId(command.getUserId());
    }

    private void updateProfileFields(UserProfileUpdateCommand command) {
        userProfileMapper.update(null, new LambdaUpdateWrapper<UserProfilePO>()
                .set(UserProfilePO::getCity, trimToEmpty(command.getCity()))
                .set(UserProfilePO::getBio, trimToEmpty(command.getBio()))
                .set(UserProfilePO::getFoodPreference, trimToEmpty(command.getFoodPreference()))
                .eq(UserProfilePO::getUserId, command.getUserId()));
    }

    private UserProfilePO queryByUserId(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfilePO>()
                .eq(UserProfilePO::getUserId, userId)
                .last("limit 1"));
    }

    private UserProfileEntity toEntity(UserPO user, UserProfilePO profile) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(user.getId());
        entity.setPhone(user.getPhone());
        entity.setNickName(user.getNickName());
        entity.setIcon(user.getIcon());
        entity.setCity(profile == null ? "" : profile.getCity());
        entity.setBio(profile == null ? "" : profile.getBio());
        entity.setFoodPreference(profile == null ? "" : profile.getFoodPreference());
        entity.setCreateTime(profile == null ? user.getCreateTime() : profile.getCreateTime());
        entity.setUpdateTime(profile == null ? user.getUpdateTime() : profile.getUpdateTime());
        return entity;
    }

    private boolean isNormalUser(UserPO user) {
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

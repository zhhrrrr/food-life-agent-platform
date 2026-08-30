package com.foodlife.user.domain.profile.repository;

import com.foodlife.user.domain.profile.model.UserProfileEntity;
import com.foodlife.user.domain.profile.model.UserProfileUpdateCommand;

public interface IUserProfileRepository {

    UserProfileEntity findProfileByUserId(Long userId);

    UserProfileEntity updateProfile(UserProfileUpdateCommand command);
}

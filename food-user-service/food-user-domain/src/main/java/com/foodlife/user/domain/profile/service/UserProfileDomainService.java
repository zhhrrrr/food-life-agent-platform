package com.foodlife.user.domain.profile.service;

import com.foodlife.user.domain.follow.repository.IUserFollowRepository;
import com.foodlife.user.domain.profile.model.UserProfileEntity;
import com.foodlife.user.domain.profile.model.UserProfileUpdateCommand;
import com.foodlife.user.domain.profile.repository.IUserProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class UserProfileDomainService {

    private final IUserProfileRepository userProfileRepository;
    private final IUserFollowRepository userFollowRepository;

    public UserProfileDomainService(IUserProfileRepository userProfileRepository, IUserFollowRepository userFollowRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userFollowRepository = userFollowRepository;
    }

    public UserProfileEntity queryMyProfile(Long userId) {
        checkLoginUser(userId);
        return fillFollowStats(userProfileRepository.findProfileByUserId(userId), userId);
    }

    public UserProfileEntity queryUserHomepage(Long currentUserId, Long targetUserId) {
        checkLoginUser(currentUserId);
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId required");
        }
        UserProfileEntity profile = userProfileRepository.findProfileByUserId(targetUserId);
        if (profile == null) {
            throw new IllegalArgumentException("user not found");
        }
        profile = fillFollowStats(profile, targetUserId);
        profile.setFollowing(!currentUserId.equals(targetUserId) && userFollowRepository.isFollowing(currentUserId, targetUserId));
        return profile;
    }

    public UserProfileEntity updateMyProfile(UserProfileUpdateCommand command) {
        checkUpdateCommand(command);
        UserProfileEntity profile = userProfileRepository.updateProfile(command);
        return fillFollowStats(profile, command.getUserId());
    }

    private UserProfileEntity fillFollowStats(UserProfileEntity profile, Long userId) {
        if (profile == null) {
            throw new IllegalArgumentException("user not found");
        }
        profile.setFollowingCount(userFollowRepository.countFollowing(userId));
        profile.setFansCount(userFollowRepository.countFans(userId));
        if (profile.getFollowing() == null) {
            profile.setFollowing(false);
        }
        return profile;
    }

    private void checkUpdateCommand(UserProfileUpdateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("profile command required");
        }
        checkLoginUser(command.getUserId());
        if (isBlank(command.getNickName())) {
            throw new IllegalArgumentException("nickName required");
        }
        checkLength(command.getNickName(), 2, 32, "nickName length invalid");
        checkMaxLength(command.getIcon(), 255, "icon too long");
        checkMaxLength(command.getCity(), 64, "city too long");
        checkMaxLength(command.getBio(), 200, "bio too long");
        checkMaxLength(command.getFoodPreference(), 200, "foodPreference too long");
    }

    private void checkLoginUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
    }

    private void checkLength(String value, int min, int max, String message) {
        String normalized = trimToEmpty(value);
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(message);
        }
    }

    private void checkMaxLength(String value, int max, String message) {
        if (value != null && value.trim().length() > max) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

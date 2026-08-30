package com.foodlife.user.domain.follow.repository;

import com.foodlife.user.domain.follow.model.FollowUserEntity;
import com.foodlife.user.domain.follow.model.UserFollowEntity;

import java.util.List;

public interface IUserFollowRepository {

    UserFollowEntity followUser(Long userId, Long followUserId);

    UserFollowEntity unfollowUser(Long userId, Long followUserId);

    boolean isFollowing(Long userId, Long followUserId);

    List<FollowUserEntity> listFollowingUsers(Long userId, Long lastId, Integer limit);

    List<FollowUserEntity> listFans(Long userId, Long lastId, Integer limit);

    List<FollowUserEntity> listCommonFollowUsers(Long userId, Long targetUserId, Integer limit);

    long countFollowing(Long userId);

    long countFans(Long userId);
}

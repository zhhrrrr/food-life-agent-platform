package com.foodlife.user.domain.follow.service;

import com.foodlife.user.domain.follow.model.FollowUserEntity;
import com.foodlife.user.domain.follow.model.FollowUserListResult;
import com.foodlife.user.domain.follow.model.UserFollowCommand;
import com.foodlife.user.domain.follow.model.UserFollowEntity;
import com.foodlife.user.domain.follow.repository.IUserFollowRepository;
import com.foodlife.user.domain.user.model.UserEntity;
import com.foodlife.user.domain.user.repository.IUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserFollowDomainService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final IUserFollowRepository userFollowRepository;
    private final IUserRepository userRepository;

    public UserFollowDomainService(IUserFollowRepository userFollowRepository, IUserRepository userRepository) {
        this.userFollowRepository = userFollowRepository;
        this.userRepository = userRepository;
    }

    public UserFollowEntity followUser(UserFollowCommand command) {
        checkCommand(command);
        checkTargetUser(command.getFollowUserId());
        return userFollowRepository.followUser(command.getUserId(), command.getFollowUserId());
    }

    public UserFollowEntity unfollowUser(UserFollowCommand command) {
        checkCommand(command);
        return userFollowRepository.unfollowUser(command.getUserId(), command.getFollowUserId());
    }

    public boolean isFollowing(Long userId, Long followUserId) {
        checkUserId(userId);
        if (followUserId == null) {
            throw new IllegalArgumentException("followUserId required");
        }
        return userFollowRepository.isFollowing(userId, followUserId);
    }

    public FollowUserListResult listFollowingUsers(Long userId, Long lastId, Integer pageSize) {
        checkUserId(userId);
        return toListResult(userFollowRepository.listFollowingUsers(userId, lastId, normalizePageSize(pageSize) + 1),
                normalizePageSize(pageSize));
    }

    public FollowUserListResult listFans(Long userId, Long lastId, Integer pageSize) {
        checkUserId(userId);
        return toListResult(userFollowRepository.listFans(userId, lastId, normalizePageSize(pageSize) + 1),
                normalizePageSize(pageSize));
    }

    public FollowUserListResult listCommonFollowUsers(Long userId, Long targetUserId, Integer pageSize) {
        checkUserId(userId);
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId required");
        }
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("target user can not be self");
        }
        checkTargetUser(targetUserId);
        int normalizedPageSize = normalizePageSize(pageSize);
        return toListResult(userFollowRepository.listCommonFollowUsers(userId, targetUserId, normalizedPageSize + 1),
                normalizedPageSize);
    }

    private void checkCommand(UserFollowCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("follow command required");
        }
        checkUserId(command.getUserId());
        if (command.getFollowUserId() == null) {
            throw new IllegalArgumentException("followUserId required");
        }
        if (command.getUserId().equals(command.getFollowUserId())) {
            throw new IllegalArgumentException("can not follow self");
        }
    }

    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("user not login");
        }
    }

    private void checkTargetUser(Long userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("follow user not found");
        }
    }

    private FollowUserListResult toListResult(List<FollowUserEntity> users, int pageSize) {
        FollowUserListResult result = new FollowUserListResult();
        boolean hasMore = users.size() > pageSize;
        if (hasMore) {
            users = users.subList(0, pageSize);
        }
        result.setUsers(users);
        result.setHasMore(hasMore);
        result.setLastId(users.isEmpty() ? null : users.get(users.size() - 1).getFollowId());
        return result;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

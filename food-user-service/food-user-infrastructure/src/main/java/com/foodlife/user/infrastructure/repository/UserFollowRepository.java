package com.foodlife.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodlife.user.domain.follow.model.FollowUserEntity;
import com.foodlife.user.domain.follow.model.UserFollowEntity;
import com.foodlife.user.domain.follow.repository.IUserFollowRepository;
import com.foodlife.user.infrastructure.dao.IUserFollowMapper;
import com.foodlife.user.infrastructure.dao.IUserMapper;
import com.foodlife.user.infrastructure.dao.po.UserFollowPO;
import com.foodlife.user.infrastructure.dao.po.UserPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class UserFollowRepository implements IUserFollowRepository {

    private final IUserFollowMapper userFollowMapper;
    private final IUserMapper userMapper;

    public UserFollowRepository(IUserFollowMapper userFollowMapper, IUserMapper userMapper) {
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
    }

    @Override
    public UserFollowEntity followUser(Long userId, Long followUserId) {
        UserFollowPO existing = queryByUserAndFollowUser(userId, followUserId);
        if (existing != null) {
            if (existing.getFollowStatus() == null || existing.getFollowStatus() != 1) {
                userFollowMapper.update(null, new LambdaUpdateWrapper<UserFollowPO>()
                        .set(UserFollowPO::getFollowStatus, 1)
                        .eq(UserFollowPO::getId, existing.getId()));
                existing = userFollowMapper.selectById(existing.getId());
            }
            return toEntity(existing);
        }

        UserFollowPO follow = new UserFollowPO();
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        follow.setFollowStatus(1);
        try {
            userFollowMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            userFollowMapper.update(null, new LambdaUpdateWrapper<UserFollowPO>()
                    .set(UserFollowPO::getFollowStatus, 1)
                    .eq(UserFollowPO::getUserId, userId)
                    .eq(UserFollowPO::getFollowUserId, followUserId));
            follow = queryByUserAndFollowUser(userId, followUserId);
            return toEntity(follow);
        }
        return toEntity(userFollowMapper.selectById(follow.getId()));
    }

    @Override
    public UserFollowEntity unfollowUser(Long userId, Long followUserId) {
        UserFollowPO existing = queryByUserAndFollowUser(userId, followUserId);
        if (existing == null) {
            UserFollowEntity entity = new UserFollowEntity();
            entity.setUserId(userId);
            entity.setFollowUserId(followUserId);
            entity.setFollowStatus(0);
            return entity;
        }
        if (existing.getFollowStatus() == null || existing.getFollowStatus() != 0) {
            userFollowMapper.update(null, new LambdaUpdateWrapper<UserFollowPO>()
                    .set(UserFollowPO::getFollowStatus, 0)
                    .eq(UserFollowPO::getId, existing.getId()));
        }
        return toEntity(userFollowMapper.selectById(existing.getId()));
    }

    @Override
    public boolean isFollowing(Long userId, Long followUserId) {
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getUserId, userId)
                .eq(UserFollowPO::getFollowUserId, followUserId)
                .eq(UserFollowPO::getFollowStatus, 1));
        return count != null && count > 0;
    }

    @Override
    public List<FollowUserEntity> listFollowingUsers(Long userId, Long lastId, Integer limit) {
        return toFollowUsers(userFollowMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<UserFollowPO>()
                        .eq(UserFollowPO::getUserId, userId).eq(UserFollowPO::getFollowStatus, 1)
                        .inSql(UserFollowPO::getFollowUserId, "SELECT id FROM user WHERE status = 1")
                        .lt(lastId != null, UserFollowPO::getId, lastId).orderByDesc(UserFollowPO::getId))
                .getRecords(), false);
    }

    @Override
    public List<FollowUserEntity> listFans(Long userId, Long lastId, Integer limit) {
        return toFollowUsers(userFollowMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<UserFollowPO>()
                        .eq(UserFollowPO::getFollowUserId, userId).eq(UserFollowPO::getFollowStatus, 1)
                        .inSql(UserFollowPO::getUserId, "SELECT id FROM user WHERE status = 1")
                        .lt(lastId != null, UserFollowPO::getId, lastId).orderByDesc(UserFollowPO::getId))
                .getRecords(), true);
    }

    @Override
    public List<FollowUserEntity> listCommonFollowUsers(Long userId, Long targetUserId, Integer limit) {
        return toFollowUsers(userFollowMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<UserFollowPO>()
                        .eq(UserFollowPO::getUserId, userId).eq(UserFollowPO::getFollowStatus, 1)
                        .inSql(UserFollowPO::getFollowUserId, "SELECT id FROM user WHERE status = 1")
                        .apply("follow_user_id IN (SELECT follow_user_id FROM user_follow WHERE user_id={0} AND follow_status=1)", targetUserId)
                        .orderByDesc(UserFollowPO::getId)).getRecords(), false);
    }

    private List<FollowUserEntity> toFollowUsers(List<UserFollowPO> follows, boolean fans) {
        if (follows.isEmpty()) return List.of();
        var ids = follows.stream().map(follow -> fans ? follow.getUserId() : follow.getFollowUserId()).toList();
        var users = userMapper.selectBatchIds(ids).stream().collect(java.util.stream.Collectors.toMap(UserPO::getId, user -> user));
        List<FollowUserEntity> result = new ArrayList<>();
        for (UserFollowPO follow : follows) {
            UserPO user = users.get(fans ? follow.getUserId() : follow.getFollowUserId());
            if (isNormalUser(user)) result.add(toFollowUserEntity(follow, user));
        }
        return result;
    }

    @Override
    public long countFollowing(Long userId) {
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getUserId, userId)
                .eq(UserFollowPO::getFollowStatus, 1));
        return count == null ? 0 : count;
    }

    @Override
    public long countFans(Long userId) {
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getFollowUserId, userId)
                .eq(UserFollowPO::getFollowStatus, 1));
        return count == null ? 0 : count;
    }

    private UserFollowPO queryByUserAndFollowUser(Long userId, Long followUserId) {
        return userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollowPO>()
                .eq(UserFollowPO::getUserId, userId)
                .eq(UserFollowPO::getFollowUserId, followUserId));
    }

    private boolean isNormalUser(UserPO user) {
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }

    private UserFollowEntity toEntity(UserFollowPO po) {
        if (po == null) {
            return null;
        }
        UserFollowEntity entity = new UserFollowEntity();
        entity.setId(po.getId());
        entity.setUserId(po.getUserId());
        entity.setFollowUserId(po.getFollowUserId());
        entity.setFollowStatus(po.getFollowStatus());
        entity.setCreateTime(po.getCreateTime());
        entity.setUpdateTime(po.getUpdateTime());
        return entity;
    }

    private FollowUserEntity toFollowUserEntity(UserFollowPO follow, UserPO user) {
        FollowUserEntity entity = new FollowUserEntity();
        entity.setFollowId(follow.getId());
        entity.setUserId(user.getId());
        entity.setNickName(user.getNickName());
        entity.setIcon(user.getIcon());
        entity.setFollowTime(follow.getUpdateTime() == null ? follow.getCreateTime() : follow.getUpdateTime());
        return entity;
    }
}

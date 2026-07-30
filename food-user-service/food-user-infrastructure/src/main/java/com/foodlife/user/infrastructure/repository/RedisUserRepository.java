package com.foodlife.user.infrastructure.repository;

import com.foodlife.user.domain.user.model.UserEntity;
import com.foodlife.user.domain.user.repository.IUserRepository;
import com.foodlife.user.types.constants.UserRedisConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
@ConditionalOnProperty(prefix = "food.user.repository", name = "type", havingValue = "redis")
public class RedisUserRepository implements IUserRepository {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUserRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public UserEntity findByPhone(String phone) {
        String id = stringRedisTemplate.opsForValue().get(UserRedisConstants.USER_PHONE_KEY + phone);
        if (id == null || id.length() == 0) {
            return null;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(UserRedisConstants.USER_DATA_KEY + id);
        if (CollectionUtils.isEmpty(userMap)) {
            return null;
        }
        return toEntity(userMap);
    }

    @Override
    public UserEntity save(UserEntity user) {
        if (user.getId() == null) {
            Long id = stringRedisTemplate.opsForValue().increment(UserRedisConstants.USER_ID_SEQUENCE_KEY);
            user.setId(id);
        }
        if (user.getCreateTime() == null) {
            user.setCreateTime(LocalDateTime.now());
        }
        user.setUpdateTime(LocalDateTime.now());

        Map<String, String> userMap = toMap(user);
        stringRedisTemplate.opsForHash().putAll(UserRedisConstants.USER_DATA_KEY + user.getId(), userMap);
        stringRedisTemplate.opsForValue().set(UserRedisConstants.USER_PHONE_KEY + user.getPhone(), String.valueOf(user.getId()));
        return user;
    }

    private UserEntity toEntity(Map<Object, Object> userMap) {
        UserEntity user = new UserEntity();
        user.setId(Long.valueOf(String.valueOf(userMap.get("id"))));
        user.setPhone(String.valueOf(userMap.get("phone")));
        user.setNickName(String.valueOf(userMap.get("nickName")));
        user.setIcon(String.valueOf(userMap.getOrDefault("icon", "")));
        return user;
    }

    private Map<String, String> toMap(UserEntity user) {
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("phone", user.getPhone());
        userMap.put("nickName", user.getNickName());
        userMap.put("icon", user.getIcon() == null ? "" : user.getIcon());
        return userMap;
    }
}

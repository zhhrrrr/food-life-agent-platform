package com.foodlife.business.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BusinessCacheSupport {

    private static final Logger log = LoggerFactory.getLogger(BusinessCacheSupport.class);

    private static final Duration SHOP_TTL = Duration.ofMinutes(10);
    private static final Duration PACKAGE_TTL = Duration.ofMinutes(5);
    private static final Duration PACKAGE_LIST_TTL = Duration.ofMinutes(3);
    private static final Duration TRADE_SNAPSHOT_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BusinessCacheSupport(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String shopKey(Long shopId) {
        return "food:cache:shop:" + shopId;
    }

    public String packageKey(Long packageId) {
        return "food:cache:package:" + packageId;
    }

    public String packageListKey(Long shopId) {
        return "food:cache:shop:" + shopId + ":packages";
    }

    public String packageTradeSnapshotKey(Long packageId) {
        return "food:cache:package:" + packageId + ":trade-snapshot";
    }

    public <T> T getObject(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("business cache read failed, key={}", key, e);
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(value, javaType);
        } catch (Exception e) {
            log.warn("business cache list read failed, key={}", key, e);
            return null;
        }
    }

    public void putObject(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("business cache write failed, key={}", key, e);
        }
    }

    public void putShop(Long shopId, Object value) {
        putObject(shopKey(shopId), value, SHOP_TTL);
    }

    public void putPackage(Long packageId, Object value) {
        putObject(packageKey(packageId), value, PACKAGE_TTL);
    }

    public void putPackageList(Long shopId, Object value) {
        putObject(packageListKey(shopId), value, PACKAGE_LIST_TTL);
    }

    public void putTradeSnapshot(Long packageId, Object value) {
        putObject(packageTradeSnapshotKey(packageId), value, TRADE_SNAPSHOT_TTL);
    }

    public void evictShop(Long shopId) {
        evict(shopKey(shopId));
    }

    public void evictPackage(Long packageId) {
        evict(packageKey(packageId));
        evict(packageTradeSnapshotKey(packageId));
    }

    public void evictPackageList(Long shopId) {
        evict(packageListKey(shopId));
    }

    public void delayedEvictShop(Long shopId) {
        delayedEvict(shopKey(shopId));
    }

    public void delayedEvictPackage(Long packageId) {
        delayedEvict(packageKey(packageId));
        delayedEvict(packageTradeSnapshotKey(packageId));
    }

    public void delayedEvictPackageList(Long shopId) {
        delayedEvict(packageListKey(shopId));
    }

    private void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("business cache evict failed, key={}", key, e);
        }
    }

    private void delayedEvict(String key) {
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> evict(key));
    }
}

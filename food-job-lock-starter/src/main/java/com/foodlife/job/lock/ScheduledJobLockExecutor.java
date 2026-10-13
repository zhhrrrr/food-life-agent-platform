package com.foodlife.job.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.TimeUnit;

public class ScheduledJobLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledJobLockExecutor.class);

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final ScheduledJobLockProperties properties;

    public ScheduledJobLockExecutor(ObjectProvider<RedissonClient> redissonClientProvider,
                                    ScheduledJobLockProperties properties) {
        this.redissonClientProvider = redissonClientProvider;
        this.properties = properties;
    }

    public void execute(String lockKey, Runnable task) {
        if (!properties.isEnabled()) {
            task.run();
            return;
        }
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient required when scheduled job lock is enabled");
        }

        String resolvedLockKey = resolveLockKey(lockKey);
        RLock lock = redissonClient.getLock(resolvedLockKey);
        boolean locked;
        try {
            long waitTimeMs = Math.max(properties.getWaitTimeMs(), 0L);
            if (properties.getLeaseTimeMs() > 0L) {
                locked = lock.tryLock(waitTimeMs, properties.getLeaseTimeMs(), TimeUnit.MILLISECONDS);
            } else {
                locked = lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("scheduled job interrupted while waiting lock, lockKey={}", resolvedLockKey);
            return;
        }
        if (!locked) {
            if (properties.isLogSkipped()) {
                LOGGER.info("scheduled job skipped because another instance holds lock, lockKey={}", resolvedLockKey);
            }
            return;
        }

        try {
            task.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String resolveLockKey(String lockKey) {
        String key = lockKey == null ? "" : lockKey.trim();
        String prefix = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix().trim();
        if (prefix.isEmpty() || key.startsWith(prefix + ":")) {
            return key;
        }
        return prefix + ":" + key;
    }
}

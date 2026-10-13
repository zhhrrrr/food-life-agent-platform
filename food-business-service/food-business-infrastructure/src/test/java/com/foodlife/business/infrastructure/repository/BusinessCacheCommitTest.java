package com.foodlife.business.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.infrastructure.cache.BusinessCacheSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

class BusinessCacheCommitTest {
    @Test void cacheDeletionWaitsUntilDatabaseCommit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        BusinessCacheSupport cache = new BusinessCacheSupport(redis, new ObjectMapper());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.evictPackage(1L);
            verifyNoInteractions(redis);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redis).delete("food:cache:package:1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    @Test void rolledBackMutationDoesNotInvalidateCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        BusinessCacheSupport cache = new BusinessCacheSupport(redis, new ObjectMapper());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.evictShop(1L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(redis);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}

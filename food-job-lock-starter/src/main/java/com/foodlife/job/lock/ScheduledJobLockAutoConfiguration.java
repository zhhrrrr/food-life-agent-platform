package com.foodlife.job.lock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties({ScheduledJobLockProperties.class, RedisProperties.class})
public class ScheduledJobLockAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "food.job.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String host = redisProperties.getHost() == null ? "127.0.0.1" : redisProperties.getHost();
        int port = redisProperties.getPort();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(redisProperties.getDatabase());
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()));
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduledJobLockExecutor scheduledJobLockExecutor(ObjectProvider<RedissonClient> redissonClientProvider,
                                                            ScheduledJobLockProperties properties) {
        return new ScheduledJobLockExecutor(redissonClientProvider, properties);
    }
}

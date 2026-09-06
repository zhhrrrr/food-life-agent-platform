package com.foodlife.observability.config;

import com.foodlife.observability.feign.ObservabilityFeignLogger;
import com.foodlife.observability.properties.ObservabilityProperties;
import feign.Logger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Logger.class)
@ConditionalOnProperty(prefix = "food.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Logger.class)
    public Logger observabilityFeignLogger(ObservabilityProperties properties) {
        return new ObservabilityFeignLogger(properties);
    }
}

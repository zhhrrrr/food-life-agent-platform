package com.foodlife.observability.config;

import com.foodlife.observability.properties.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "food.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistryCustomizer<MeterRegistry> foodMeterRegistryCustomizer(Environment environment) {
        return registry -> registry.config().commonTags(
                "application", environment.getProperty("spring.application.name", "food-life-service")
        );
    }
}

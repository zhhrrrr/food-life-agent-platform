package com.foodlife.observability.config;

import com.foodlife.observability.filter.ReactiveTraceWebFilter;
import com.foodlife.observability.properties.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@ConditionalOnClass(WebFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "food.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveTraceWebFilter reactiveTraceWebFilter(ObservabilityProperties properties,
                                                         MeterRegistry meterRegistry,
                                                         Environment environment) {
        return new ReactiveTraceWebFilter(properties, meterRegistry, environment);
    }
}

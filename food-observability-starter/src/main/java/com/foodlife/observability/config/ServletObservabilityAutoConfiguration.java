package com.foodlife.observability.config;

import com.foodlife.observability.filter.ServletTraceFilter;
import com.foodlife.observability.properties.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "food.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ServletObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServletTraceFilter servletTraceFilter(ObservabilityProperties properties,
                                                 MeterRegistry meterRegistry,
                                                 Environment environment) {
        return new ServletTraceFilter(properties, meterRegistry, environment);
    }
}

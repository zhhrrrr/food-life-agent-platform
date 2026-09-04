package com.foodlife.auth.config;

import com.foodlife.auth.feign.FeignAuthRequestInterceptor;
import com.foodlife.auth.interceptor.InternalCallInterceptor;
import com.foodlife.auth.interceptor.LoginInterceptor;
import com.foodlife.auth.interceptor.RefreshTokenInterceptor;
import com.foodlife.auth.properties.AuthProperties;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@ConditionalOnProperty(prefix = "food.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthAutoConfiguration implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    public AuthAutoConfiguration(StringRedisTemplate stringRedisTemplate, AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authProperties = authProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate, authProperties))
                .addPathPatterns("/**")
                .order(0);

        if (authProperties.getInternalCall() != null
                && authProperties.getInternalCall().getPaths() != null
                && !authProperties.getInternalCall().getPaths().isEmpty()) {
            registry.addInterceptor(new InternalCallInterceptor(authProperties))
                    .addPathPatterns(authProperties.getInternalCall().getPaths())
                    .order(1);
        }

        registry.addInterceptor(new LoginInterceptor(buildLoginExcludePaths()))
                .addPathPatterns(authProperties.getIncludePaths())
                .excludePathPatterns(buildLoginExcludePaths())
                .order(2);
    }

    @Bean
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnMissingBean(FeignAuthRequestInterceptor.class)
    public RequestInterceptor feignAuthRequestInterceptor() {
        return new FeignAuthRequestInterceptor(authProperties);
    }

    private List<String> buildLoginExcludePaths() {
        List<String> paths = new ArrayList<>(Arrays.asList(
                "/health",
                "/error",
                "/api/user/code",
                "/api/user/login",
                "/api/user/logout"
        ));
        if (authProperties.getExcludePaths() != null) {
            paths.addAll(authProperties.getExcludePaths());
        }
        return paths;
    }
}

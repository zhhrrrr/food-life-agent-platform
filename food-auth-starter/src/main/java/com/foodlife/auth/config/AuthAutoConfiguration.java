package com.foodlife.auth.config;

import com.foodlife.auth.feign.FeignAuthRequestInterceptor;
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

        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(authProperties.getIncludePaths())
                .excludePathPatterns(authProperties.getExcludePaths())
                .order(1);
    }

    @Bean
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnMissingBean(RequestInterceptor.class)
    public RequestInterceptor feignAuthRequestInterceptor() {
        return new FeignAuthRequestInterceptor(authProperties);
    }
}

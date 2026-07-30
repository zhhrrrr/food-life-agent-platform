package com.foodlife.auth.interceptor;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.auth.properties.AuthProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, AuthProperties authProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(authProperties.getTokenHeader());
        if (!StringUtils.hasText(token)) {
            return true;
        }

        String tokenKey = authProperties.getTokenPrefix() + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (CollectionUtils.isEmpty(userMap)) {
            return true;
        }

        LoginUserDTO user = new LoginUserDTO();
        Object id = userMap.get("id");
        user.setId(id == null ? null : Long.valueOf(String.valueOf(id)));
        user.setNickName((String) userMap.get("nickName"));
        user.setIcon((String) userMap.get("icon"));

        UserHolder.saveUser(user);
        stringRedisTemplate.expire(tokenKey, authProperties.getTokenTtlMinutes(), TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}

package com.foodlife.auth.interceptor;

import com.foodlife.auth.properties.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class InternalCallInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;

    public InternalCallInterceptor(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (authProperties.getInternalCall() == null || !authProperties.getInternalCall().isEnabled()) {
            return true;
        }
        String internalFlag = request.getHeader(authProperties.getInternalCall().getHeaderName());
        String internalSecret = request.getHeader(authProperties.getInternalCall().getSecretHeaderName());
        if (authProperties.getInternalCall().getHeaderValue().equals(internalFlag)
                && StringUtils.hasText(internalSecret)
                && internalSecret.equals(authProperties.getInternalCall().getSecret())) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"403\",\"message\":\"forbidden\"}");
        return false;
    }
}

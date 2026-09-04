package com.foodlife.auth.feign;

import com.foodlife.auth.properties.AuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class FeignAuthRequestInterceptor implements RequestInterceptor {

    private final AuthProperties authProperties;

    public FeignAuthRequestInterceptor(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void apply(RequestTemplate template) {
        applyInternalHeaders(template);
        applyUserToken(template);
    }

    private void applyInternalHeaders(RequestTemplate template) {
        AuthProperties.InternalCall internalCall = authProperties.getInternalCall();
        if (internalCall == null || !internalCall.isEnabled()) {
            return;
        }
        template.header(internalCall.getHeaderName(), internalCall.getHeaderValue());
        template.header(internalCall.getSecretHeaderName(), internalCall.getSecret());
    }

    private void applyUserToken(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return;
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        String token = request.getHeader(authProperties.getTokenHeader());
        if (StringUtils.hasText(token)) {
            template.header(authProperties.getTokenHeader(), token);
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}

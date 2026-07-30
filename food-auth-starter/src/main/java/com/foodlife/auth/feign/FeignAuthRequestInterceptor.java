package com.foodlife.auth.feign;

import com.foodlife.auth.properties.AuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
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
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return;
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        String token = request.getHeader(authProperties.getTokenHeader());
        if (token != null && token.length() > 0) {
            template.header(authProperties.getTokenHeader(), token);
        }
    }
}

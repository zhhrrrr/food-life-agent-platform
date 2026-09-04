package com.foodlife.auth.interceptor;

import com.foodlife.auth.context.UserHolder;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LoginInterceptor implements HandlerInterceptor {

    private final List<String> excludePaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public LoginInterceptor(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? Collections.emptyList() : excludePaths;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isExcluded(request)) {
            return true;
        }
        if (UserHolder.getUser() == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"401\",\"message\":\"user not logged in\"}");
            return false;
        }
        return true;
    }

    private boolean isExcluded(HttpServletRequest request) {
        Set<String> candidatePaths = new LinkedHashSet<>();
        addPathCandidate(candidatePaths, request.getRequestURI());
        addPathCandidate(candidatePaths, request.getServletPath());

        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (StringUtils.hasText(contextPath) && StringUtils.hasText(requestUri)
                && requestUri.startsWith(contextPath)) {
            addPathCandidate(candidatePaths, requestUri.substring(contextPath.length()));
        }

        for (String pattern : excludePaths) {
            for (String path : candidatePaths) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addPathCandidate(Set<String> candidatePaths, String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String normalizedPath = normalizePath(path);
        candidatePaths.add(normalizedPath);
        if (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
            candidatePaths.add(normalizedPath.substring(0, normalizedPath.length() - 1));
        }
    }

    private String normalizePath(String path) {
        String normalizedPath = path;
        int queryIndex = normalizedPath.indexOf('?');
        if (queryIndex >= 0) {
            normalizedPath = normalizedPath.substring(0, queryIndex);
        }
        int matrixIndex = normalizedPath.indexOf(';');
        if (matrixIndex >= 0) {
            normalizedPath = normalizedPath.substring(0, matrixIndex);
        }
        return normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath;
    }
}

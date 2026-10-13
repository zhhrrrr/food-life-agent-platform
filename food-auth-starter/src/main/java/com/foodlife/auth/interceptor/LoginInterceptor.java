package com.foodlife.auth.interceptor;

import com.foodlife.auth.context.UserHolder;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LoginInterceptor implements HandlerInterceptor {

    private final List<String> excludePaths;
    /**
     * Ant风格路径匹配器
     *
     * 支持：
     *
     * /user/**
     *
     * 可以匹配：
     *
     * /user/login
     * /user/info/list
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public LoginInterceptor(List<String> excludePaths) {
        /**
         * 如果没有配置排除路径
         * 使用空集合，避免后续遍历空指针
         */
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

    // candidatePaths:
    //
    /// food/api/user/login
    /// api/user/login
    // 两者描述的是不同层面的路径，拦截器想兼容所有部署方式，所以两个
    private boolean isExcluded(HttpServletRequest request) {
        Set<String> candidatePaths = new LinkedHashSet<>();
        /**
         * 获取完整请求路径
         *
         * 例如：
         *
         * /food/api/user/login
         */
        addPathCandidate(candidatePaths, request.getRequestURI());
        /**
         * 获取Servlet处理路径
         *
         * 例如：
         *
         * /api/user/login
         */
        addPathCandidate(candidatePaths, request.getServletPath());

        /**
         * 获取项目部署前缀
         *
         * 例如：
         *
         * server.servlet.context-path=/food
         *
         * contextPath=/food
         */
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

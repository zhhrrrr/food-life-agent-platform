package com.foodlife.auth.interceptor;

import com.foodlife.auth.context.UserHolder;
import com.foodlife.auth.model.LoginUserDTO;
import com.foodlife.auth.properties.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class RoleAccessInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RoleAccessInterceptor(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        AuthProperties.PathRole pathRole = findMatchedPathRole(request);
        if (pathRole == null) {
            return true;
        }

        LoginUserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            write(response, HttpStatus.UNAUTHORIZED, "401", "user not logged in");
            return false;
        }

        String role = normalizeRole(user.getRole());
        if (isAllowed(role, pathRole.getRoles())) {
            return true;
        }

        write(response, HttpStatus.FORBIDDEN, "403", "role access denied");
        return false;
    }

    private AuthProperties.PathRole findMatchedPathRole(HttpServletRequest request) {
        String requestPath = normalizePath(request.getRequestURI());
        List<AuthProperties.PathRole> paths = authProperties.getRoleAccess().getPaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        for (AuthProperties.PathRole pathRole : paths) {
            if (pathRole != null
                    && StringUtils.hasText(pathRole.getPattern())
                    && pathMatcher.match(pathRole.getPattern(), requestPath)) {
                return pathRole;
            }
        }
        return null;
    }

    private boolean isAllowed(String role, List<String> allowedRoles) {
        if (!StringUtils.hasText(role) || allowedRoles == null || allowedRoles.isEmpty()) {
            return false;
        }
        for (String allowedRole : allowedRoles) {
            if (role.equals(normalizeRole(allowedRole))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase() : "";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        int queryIndex = path.indexOf('?');
        String normalizedPath = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        return normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath;
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}

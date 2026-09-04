package com.foodlife.gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "food.gateway.auth")
public class GatewayAuthProperties {

    private Boolean enabled = true;
    private String tokenHeader = "authorization";
    private String tokenPrefix = "food:login:token:";
    private String userIdHeader = "x-user-id";
    private String userNicknameHeader = "x-user-nickname";
    private String userIconHeader = "x-user-icon";
    private String internalHeaderName = "x-internal-call";
    private String internalSecretHeaderName = "x-internal-secret";
    private List<String> excludePaths = new ArrayList<>(Arrays.asList(
            "/health",
            "/api/user/code",
            "/api/user/login",
            "/api/user/logout",
            "/api/shop-category/**",
            "/api/shop/**",
            "/api/package/**",
            "/api/trade/pay/callback/**"
    ));

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getUserIdHeader() {
        return userIdHeader;
    }

    public void setUserIdHeader(String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }

    public String getUserNicknameHeader() {
        return userNicknameHeader;
    }

    public void setUserNicknameHeader(String userNicknameHeader) {
        this.userNicknameHeader = userNicknameHeader;
    }

    public String getUserIconHeader() {
        return userIconHeader;
    }

    public void setUserIconHeader(String userIconHeader) {
        this.userIconHeader = userIconHeader;
    }

    public String getInternalHeaderName() {
        return internalHeaderName;
    }

    public void setInternalHeaderName(String internalHeaderName) {
        this.internalHeaderName = internalHeaderName;
    }

    public String getInternalSecretHeaderName() {
        return internalSecretHeaderName;
    }

    public void setInternalSecretHeaderName(String internalSecretHeaderName) {
        this.internalSecretHeaderName = internalSecretHeaderName;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}

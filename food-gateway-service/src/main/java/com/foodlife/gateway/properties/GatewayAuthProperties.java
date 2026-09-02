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

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}

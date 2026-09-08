package com.foodlife.gateway.filter;

import com.foodlife.gateway.support.GatewayErrorResponse;
import com.foodlife.gateway.properties.GatewaySecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class GatewayBlacklistFilter implements WebFilter, Ordered {

    private final GatewaySecurityProperties securityProperties;
    private final Environment environment;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayBlacklistFilter(GatewaySecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getRawPath();
        if (isManagementActuatorRequest(exchange, path)) {
            return chain.filter(exchange);
        }

        if (isBlacklisted(path)) {
            return forbidden(exchange);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private boolean isBlacklisted(String path) {
        GatewaySecurityProperties.Blacklist blacklist = securityProperties.getBlacklist();
        if (blacklist == null || !Boolean.TRUE.equals(blacklist.getEnabled())) {
            return false;
        }
        List<String> paths = blacklist.getPaths();
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        for (String pattern : paths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isManagementActuatorRequest(ServerWebExchange exchange, String path) {
        if (path == null || !path.startsWith("/actuator")) {
            return false;
        }
        Integer managementPort = environment.getProperty("management.server.port", Integer.class);
        if (managementPort == null || exchange.getRequest().getLocalAddress() == null) {
            return false;
        }
        return managementPort.equals(exchange.getRequest().getLocalAddress().getPort());
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        return GatewayErrorResponse.write(exchange, HttpStatus.FORBIDDEN, "403", "forbidden");
    }
}

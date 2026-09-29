package com.foodlife.gateway.filter;

import com.foodlife.gateway.properties.GatewayAuthProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AgentGatewayTrustFilter implements GlobalFilter, Ordered {

    private static final String AGENT_PATH_PREFIX = "/api/agent/";

    private final GatewayAuthProperties authProperties;
    private final String internalSecret;

    public AgentGatewayTrustFilter(
            GatewayAuthProperties authProperties,
            @Value("${food.auth.internal-call.secret:${FOOD_INTERNAL_SECRET:local-internal-secret}}")
            String internalSecret) {
        this.authProperties = authProperties;
        this.internalSecret = internalSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (!path.startsWith(AGENT_PATH_PREFIX) || !StringUtils.hasText(internalSecret)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(
                        authProperties.getInternalSecretHeaderName(),
                        internalSecret))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}

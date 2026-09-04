package com.foodlife.gateway.filter;

import com.foodlife.gateway.properties.GatewayAuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final byte[] UNAUTHORIZED_BODY = "{\"code\":\"401\",\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);

    private final GatewayAuthProperties gatewayAuthProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthFilter(GatewayAuthProperties gatewayAuthProperties,
                             ReactiveStringRedisTemplate redisTemplate) {
        this.gatewayAuthProperties = gatewayAuthProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = sanitizedExchange(exchange);
        if (!Boolean.TRUE.equals(gatewayAuthProperties.getEnabled())
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || isExcluded(exchange.getRequest().getURI().getRawPath())) {
            return chain.filter(sanitizedExchange);
        }

        String token = readToken(sanitizedExchange);
        if (!StringUtils.hasText(token)) {
            return unauthorized(sanitizedExchange);
        }

        String redisKey = gatewayAuthProperties.getTokenPrefix() + token;
        return redisTemplate.opsForHash().entries(redisKey)
                .collectMap(entry -> String.valueOf(entry.getKey()), entry -> String.valueOf(entry.getValue()))
                .flatMap(userMap -> userMap.isEmpty()
                        ? unauthorized(sanitizedExchange)
                        : chain.filter(mutatedExchangeWithUser(sanitizedExchange, token, userMap)))
                .onErrorResume(e -> unauthorized(sanitizedExchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isExcluded(String path) {
        if (gatewayAuthProperties.getExcludePaths() == null || gatewayAuthProperties.getExcludePaths().isEmpty()) {
            return false;
        }
        for (String pattern : gatewayAuthProperties.getExcludePaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String readToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(gatewayAuthProperties.getTokenHeader());
        if (!StringUtils.hasText(token)) {
            token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        token = token.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token;
    }

    private ServerWebExchange sanitizedExchange(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(gatewayAuthProperties.getUserIdHeader());
                    headers.remove(gatewayAuthProperties.getUserNicknameHeader());
                    headers.remove(gatewayAuthProperties.getUserIconHeader());
                    headers.remove(gatewayAuthProperties.getInternalHeaderName());
                    headers.remove(gatewayAuthProperties.getInternalSecretHeaderName());
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange mutatedExchangeWithUser(ServerWebExchange exchange, String token, Map<String, String> userMap) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(gatewayAuthProperties.getTokenHeader(), token);
                    headers.set(HttpHeaders.AUTHORIZATION, token);
                    setHeaderIfPresent(headers, gatewayAuthProperties.getUserIdHeader(), userMap.get("id"));
                    setHeaderIfPresent(headers, gatewayAuthProperties.getUserNicknameHeader(), userMap.get("nickName"));
                    setHeaderIfPresent(headers, gatewayAuthProperties.getUserIconHeader(), userMap.get("icon"));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private void setHeaderIfPresent(HttpHeaders headers, String headerName, String value) {
        if (StringUtils.hasText(headerName) && StringUtils.hasText(value)) {
            headers.set(headerName, sanitizeHeaderValue(value));
        }
    }

    private String sanitizeHeaderValue(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED_BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

package com.foodlife.gateway.filter;

import com.foodlife.gateway.properties.GatewayAuthProperties;
import com.foodlife.gateway.properties.GatewaySecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Component
public class GatewayTrafficGuardFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayTrafficGuardFilter.class);

    private static final byte[] FORBIDDEN_BODY = "{\"code\":\"403\",\"message\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TOO_MANY_REQUESTS_BODY = "{\"code\":\"429\",\"message\":\"too many requests\"}".getBytes(StandardCharsets.UTF_8);
    private static final String RATE_LIMIT_PREFIX = "food:gateway:rate-limit:";

    private final GatewaySecurityProperties securityProperties;
    private final GatewayAuthProperties authProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayTrafficGuardFilter(GatewaySecurityProperties securityProperties,
                                     GatewayAuthProperties authProperties,
                                     ReactiveStringRedisTemplate redisTemplate) {
        this.securityProperties = securityProperties;
        this.authProperties = authProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getRawPath();
        if (isBlacklisted(path)) {
            return forbidden(exchange);
        }

        GatewaySecurityProperties.RateLimit rateLimit = securityProperties.getRateLimit();
        if (rateLimit == null || !Boolean.TRUE.equals(rateLimit.getEnabled())) {
            return chain.filter(exchange);
        }

        return applyIpLimit(exchange, () -> applyUserLimit(exchange, () -> chain.filter(exchange)));
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

    private Mono<Void> applyIpLimit(ServerWebExchange exchange, Supplier<Mono<Void>> next) {
        GatewaySecurityProperties.Limit limit = securityProperties.getRateLimit().getIp();
        if (!isLimitEnabled(limit)) {
            return next.get();
        }
        String clientIp = resolveClientIp(exchange);
        String key = RATE_LIMIT_PREFIX + "ip:" + hash(clientIp) + ":" + currentBucket(limit.getWindowSeconds());
        return applyLimit(exchange, key, limit, next);
    }

    private Mono<Void> applyUserLimit(ServerWebExchange exchange, Supplier<Mono<Void>> next) {
        GatewaySecurityProperties.Limit limit = securityProperties.getRateLimit().getUser();
        if (!isLimitEnabled(limit)) {
            return next.get();
        }
        String token = readToken(exchange);
        if (!StringUtils.hasText(token)) {
            return next.get();
        }
        String key = RATE_LIMIT_PREFIX + "user:" + hash(token) + ":" + currentBucket(limit.getWindowSeconds());
        return applyLimit(exchange, key, limit, next);
    }

    private boolean isLimitEnabled(GatewaySecurityProperties.Limit limit) {
        return limit != null
                && Boolean.TRUE.equals(limit.getEnabled())
                && limit.getCapacity() != null
                && limit.getCapacity() > 0
                && limit.getWindowSeconds() != null
                && limit.getWindowSeconds() > 0;
    }

    private Mono<Void> applyLimit(ServerWebExchange exchange,
                                  String key,
                                  GatewaySecurityProperties.Limit limit,
                                  Supplier<Mono<Void>> next) {
        return checkLimit(key, limit)
                .flatMap(allowed -> Boolean.TRUE.equals(allowed) ? next.get() : tooManyRequests(exchange));
    }

    private Mono<Boolean> checkLimit(String key, GatewaySecurityProperties.Limit limit) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Void> expireIfFirstHit = count != null && count == 1L
                            ? redisTemplate.expire(key, Duration.ofSeconds(limit.getWindowSeconds())).then()
                            : Mono.empty();
                    boolean allowed = count == null || count <= limit.getCapacity();
                    return expireIfFirstHit.thenReturn(allowed);
                })
                .onErrorResume(e -> {
                    log.warn("gateway rate limit failed open, key={}", key, e);
                    return Mono.just(true);
                });
    }

    private long currentBucket(int windowSeconds) {
        return Instant.now().getEpochSecond() / windowSeconds;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String readToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(authProperties.getTokenHeader());
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

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not supported", e);
        }
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, FORBIDDEN_BODY);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        return writeJson(exchange, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_BODY);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, byte[] body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

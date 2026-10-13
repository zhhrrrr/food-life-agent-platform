package com.foodlife.gateway.filter;

import com.foodlife.gateway.properties.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayAuthFilterTest {
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveHashOperations<String, Object, Object> hashes = mock(ReactiveHashOperations.class);
    private final GatewayAuthFilter filter = new GatewayAuthFilter(new GatewayAuthProperties(), redis);

    @Test void validSessionDoesNotTurnDownstreamFailureIntoUnauthorized() {
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Flux.just(new AbstractMap.SimpleEntry<>("id", "1")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trade/orders").header("authorization", "test"));
        StepVerifier.create(filter.filter(exchange, value -> Mono.error(new IllegalStateException("downstream unavailable"))))
                .expectErrorMessage("downstream unavailable").verify();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void redisFailureStillRejectsRequestWithoutCallingBusinessService() {
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Flux.error(new IllegalStateException("redis unavailable")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/trade/orders").header("authorization", "test"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test void publicRequestCannotForwardForgedIdentityOrInternalServiceHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health")
                .header("x-user-id", "999").header("x-internal-call", "true").header("x-internal-secret", "forged"));
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();
        StepVerifier.create(filter.filter(exchange, value -> { forwarded.set(value); return Mono.empty(); })).verifyComplete();
        assertThat(forwarded.get().getRequest().getHeaders()).doesNotContainKeys("x-user-id", "x-internal-call", "x-internal-secret");
    }
}

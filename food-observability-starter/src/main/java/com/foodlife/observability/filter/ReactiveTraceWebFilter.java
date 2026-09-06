package com.foodlife.observability.filter;

import com.foodlife.observability.properties.ObservabilityProperties;
import com.foodlife.observability.support.TraceIdSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

public class ReactiveTraceWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTraceWebFilter.class);

    private final ObservabilityProperties properties;
    private final MeterRegistry meterRegistry;
    private final String applicationName;

    public ReactiveTraceWebFilter(ObservabilityProperties properties, MeterRegistry meterRegistry, Environment environment) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.applicationName = environment.getProperty("spring.application.name", "food-life-service");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path != null && path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        long startNanos = System.nanoTime();
        String traceId = TraceIdSupport.currentOrNew(exchange.getRequest().getHeaders().getFirst(properties.getTraceHeader()));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(properties.getTraceHeader(), traceId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getResponse().getHeaders().set(properties.getTraceHeader(), traceId);

        return chain.filter(mutatedExchange)
                .doOnEach(signal -> {
                    if (!signal.isOnComplete() && !signal.hasValue()) {
                        return;
                    }
                    MDC.put(TraceIdSupport.TRACE_ID, traceId);
                    MDC.put(TraceIdSupport.TRACE_ID_COMPAT, traceId);
                })
                .doFinally(signalType -> {
                    TraceIdSupport.put(traceId);
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    int status = resolveStatus(mutatedExchange);
                    record(request.getMethod() == null ? "UNKNOWN" : request.getMethod().name(), path, status, durationMs);
                    logRequest(request, path, status, durationMs, traceId);
                    TraceIdSupport.clear();
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private int resolveStatus(ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        return statusCode == null ? 200 : statusCode.value();
    }

    private void record(String method, String path, int status, long durationMs) {
        String safePath = StringUtils.hasText(path) ? path : "UNKNOWN";
        Timer.builder("food_http_server_request_duration")
                .description("Food gateway request duration")
                .tag("method", method)
                .tag("path", safePath)
                .tag("status", String.valueOf(status))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
        if (durationMs >= properties.getSlowApiThresholdMs()) {
            Counter.builder("food_http_server_slow_request_total")
                    .description("Food gateway slow request count")
                    .tag("application", applicationName)
                    .tag("method", method)
                    .tag("path", safePath)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void logRequest(ServerHttpRequest request, String path, int status, long durationMs, String traceId) {
        if (!properties.isRequestLogEnabled()) {
            return;
        }
        String message = "http request finished, service={}, traceId={}, method={}, path={}, status={}, durationMs={}";
        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().name();
        if (durationMs >= properties.getSlowApiThresholdMs()) {
            log.warn(message, applicationName, traceId, method, path, status, durationMs);
        } else if (properties.isLogNormalRequest()) {
            log.info(message, applicationName, traceId, method, path, status, durationMs);
        }
    }
}

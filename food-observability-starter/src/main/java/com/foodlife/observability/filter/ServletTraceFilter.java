package com.foodlife.observability.filter;

import com.foodlife.observability.properties.ObservabilityProperties;
import com.foodlife.observability.support.TraceIdSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ServletTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServletTraceFilter.class);

    private final ObservabilityProperties properties;
    private final MeterRegistry meterRegistry;
    private final String applicationName;

    public ServletTraceFilter(ObservabilityProperties properties, MeterRegistry meterRegistry, Environment environment) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.applicationName = environment.getProperty("spring.application.name", "food-life-service");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = TraceIdSupport.currentOrNew(request.getHeader(properties.getTraceHeader()));
        TraceIdSupport.put(traceId);
        response.setHeader(properties.getTraceHeader(), traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            record(request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            logRequest(request, response.getStatus(), durationMs, traceId);
            TraceIdSupport.clear();
        }
    }

    private void record(String method, String path, int status, long durationMs) {
        String safePath = StringUtils.hasText(path) ? path : "UNKNOWN";
        Timer.builder("food_http_server_request_duration")
                .description("Food service request duration")
                .tag("method", method)
                .tag("path", safePath)
                .tag("status", String.valueOf(status))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
        if (durationMs >= properties.getSlowApiThresholdMs()) {
            Counter.builder("food_http_server_slow_request_total")
                    .description("Food service slow request count")
                    .tag("application", applicationName)
                    .tag("method", method)
                    .tag("path", safePath)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void logRequest(HttpServletRequest request, int status, long durationMs, String traceId) {
        if (!properties.isRequestLogEnabled()) {
            return;
        }
        String message = "http request finished, service={}, traceId={}, method={}, path={}, status={}, durationMs={}";
        if (durationMs >= properties.getSlowApiThresholdMs()) {
            log.warn(message, applicationName, traceId, request.getMethod(), request.getRequestURI(), status, durationMs);
        } else if (properties.isLogNormalRequest()) {
            log.info(message, applicationName, traceId, request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}

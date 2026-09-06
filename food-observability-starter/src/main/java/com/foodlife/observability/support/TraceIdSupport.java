package com.foodlife.observability.support;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class TraceIdSupport {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_COMPAT = "trace-id";

    private TraceIdSupport() {
    }

    public static String currentOrNew(String candidate) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        String traceId = MDC.get(TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        traceId = MDC.get(TRACE_ID_COMPAT);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void put(String traceId) {
        MDC.put(TRACE_ID, traceId);
        MDC.put(TRACE_ID_COMPAT, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
        MDC.remove(TRACE_ID_COMPAT);
    }
}

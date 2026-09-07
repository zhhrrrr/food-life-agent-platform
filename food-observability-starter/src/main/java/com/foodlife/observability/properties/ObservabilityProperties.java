package com.foodlife.observability.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "food.observability")
public class ObservabilityProperties {

    private boolean enabled = true;
    private String traceHeader = "X-Trace-Id";
    private boolean requestLogEnabled = true;
    private boolean logNormalRequest = true;
    private long slowApiThresholdMs = 1000;
    private long slowFeignThresholdMs = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTraceHeader() {
        return traceHeader;
    }

    public void setTraceHeader(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    public boolean isRequestLogEnabled() {
        return requestLogEnabled;
    }

    public void setRequestLogEnabled(boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public boolean isLogNormalRequest() {
        return logNormalRequest;
    }

    public void setLogNormalRequest(boolean logNormalRequest) {
        this.logNormalRequest = logNormalRequest;
    }

    public long getSlowApiThresholdMs() {
        return slowApiThresholdMs;
    }

    public void setSlowApiThresholdMs(long slowApiThresholdMs) {
        this.slowApiThresholdMs = slowApiThresholdMs;
    }

    public long getSlowFeignThresholdMs() {
        return slowFeignThresholdMs;
    }

    public void setSlowFeignThresholdMs(long slowFeignThresholdMs) {
        this.slowFeignThresholdMs = slowFeignThresholdMs;
    }
}

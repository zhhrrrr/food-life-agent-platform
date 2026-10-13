package com.foodlife.job.lock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "food.job.lock")
public class ScheduledJobLockProperties {

    private boolean enabled = true;
    private String keyPrefix = "food:job:lock";
    private long waitTimeMs = 0L;
    private long leaseTimeMs = -1L;
    private boolean logSkipped = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getWaitTimeMs() {
        return waitTimeMs;
    }

    public void setWaitTimeMs(long waitTimeMs) {
        this.waitTimeMs = waitTimeMs;
    }

    public long getLeaseTimeMs() {
        return leaseTimeMs;
    }

    public void setLeaseTimeMs(long leaseTimeMs) {
        this.leaseTimeMs = leaseTimeMs;
    }

    public boolean isLogSkipped() {
        return logSkipped;
    }

    public void setLogSkipped(boolean logSkipped) {
        this.logSkipped = logSkipped;
    }
}

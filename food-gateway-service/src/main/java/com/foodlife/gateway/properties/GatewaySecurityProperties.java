package com.foodlife.gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "food.gateway.security")
public class GatewaySecurityProperties {

    private Blacklist blacklist = new Blacklist();
    private RateLimit rateLimit = new RateLimit();

    public Blacklist getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Blacklist {
        private Boolean enabled = true;
        private List<String> paths = new ArrayList<>(Arrays.asList(
                "/internal/**",
                "/actuator/**"
        ));

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }

    public static class RateLimit {
        private Boolean enabled = true;
        private Limit ip = new Limit(true, 120, 60);
        private Limit user = new Limit(true, 60, 60);

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Limit getIp() {
            return ip;
        }

        public void setIp(Limit ip) {
            this.ip = ip;
        }

        public Limit getUser() {
            return user;
        }

        public void setUser(Limit user) {
            this.user = user;
        }
    }

    public static class Limit {
        private Boolean enabled = true;
        private Integer capacity = 60;
        private Integer windowSeconds = 60;

        public Limit() {
        }

        public Limit(Boolean enabled, Integer capacity, Integer windowSeconds) {
            this.enabled = enabled;
            this.capacity = capacity;
            this.windowSeconds = windowSeconds;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getCapacity() {
            return capacity;
        }

        public void setCapacity(Integer capacity) {
            this.capacity = capacity;
        }

        public Integer getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(Integer windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}

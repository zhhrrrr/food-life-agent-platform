package com.foodlife.gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "food.gateway.sentinel")
public class GatewaySentinelProperties {

    private Boolean enabled = true;
    private Double tradeRouteQps = 300D;
    private Double businessRouteQps = 500D;
    private Double userRouteQps = 200D;
    private Double tradeOrderCreateQps = 80D;
    private Double seckillOrderCreateQps = 30D;
    private Double paymentCallbackQps = 100D;
    private String userHeaderName = "authorization";
    private Double userHeaderQps = 20D;
    private Boolean smokeRuleEnabled = true;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Double getTradeRouteQps() {
        return tradeRouteQps;
    }

    public void setTradeRouteQps(Double tradeRouteQps) {
        this.tradeRouteQps = tradeRouteQps;
    }

    public Double getBusinessRouteQps() {
        return businessRouteQps;
    }

    public void setBusinessRouteQps(Double businessRouteQps) {
        this.businessRouteQps = businessRouteQps;
    }

    public Double getUserRouteQps() {
        return userRouteQps;
    }

    public void setUserRouteQps(Double userRouteQps) {
        this.userRouteQps = userRouteQps;
    }

    public Double getTradeOrderCreateQps() {
        return tradeOrderCreateQps;
    }

    public void setTradeOrderCreateQps(Double tradeOrderCreateQps) {
        this.tradeOrderCreateQps = tradeOrderCreateQps;
    }

    public Double getSeckillOrderCreateQps() {
        return seckillOrderCreateQps;
    }

    public void setSeckillOrderCreateQps(Double seckillOrderCreateQps) {
        this.seckillOrderCreateQps = seckillOrderCreateQps;
    }

    public Double getPaymentCallbackQps() {
        return paymentCallbackQps;
    }

    public void setPaymentCallbackQps(Double paymentCallbackQps) {
        this.paymentCallbackQps = paymentCallbackQps;
    }

    public String getUserHeaderName() {
        return userHeaderName;
    }

    public void setUserHeaderName(String userHeaderName) {
        this.userHeaderName = userHeaderName;
    }

    public Double getUserHeaderQps() {
        return userHeaderQps;
    }

    public void setUserHeaderQps(Double userHeaderQps) {
        this.userHeaderQps = userHeaderQps;
    }

    public Boolean getSmokeRuleEnabled() {
        return smokeRuleEnabled;
    }

    public void setSmokeRuleEnabled(Boolean smokeRuleEnabled) {
        this.smokeRuleEnabled = smokeRuleEnabled;
    }
}

package com.foodlife.trade.infrastructure.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "food.payment.alipay")
public class AlipaySandboxProperties {
    private String appId;
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String sellerId;
    private String privateKeyPath;
    private String publicKeyPath;
    private String notifyUrl;
    private String returnUrl = "http://localhost:5173/orders";
}

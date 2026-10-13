package com.foodlife.trade.infrastructure.payment;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(prefix = "food.payment.alipay", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AlipaySandboxProperties.class)
public class AlipaySandboxConfiguration {
    @Bean
    public AlipayClient alipaySandboxClient(AlipaySandboxProperties properties) throws Exception {
        // This provider is intentionally limited to the sandbox.
        URI gateway = URI.create(properties.getGatewayUrl());
        if (!"https".equals(gateway.getScheme())
                || !"openapi-sandbox.dl.alipaydev.com".equals(gateway.getHost())
                || !"/gateway.do".equals(gateway.getPath())) {
            throw new IllegalArgumentException("Alipay sandbox gateway required");
        }
        require(properties.getAppId(), "app-id");
        require(properties.getSellerId(), "seller-id");
        URI notify = URI.create(require(properties.getNotifyUrl(), "notify-url"));
        if (!"https".equals(notify.getScheme()) || notify.getHost() == null) {
            throw new IllegalArgumentException("Alipay notify-url must be public HTTPS");
        }
        URI.create(require(properties.getReturnUrl(), "return-url"));
        String privateKey = readKey(properties.getPrivateKeyPath());
        String publicKey = readKey(properties.getPublicKeyPath());
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Alipay keys invalid: PKCS8 RSA private key and X509 RSA public key required");
        }
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(privateKey);
        config.setAlipayPublicKey(publicKey);
        config.setFormat("json");
        config.setCharset("utf-8");
        config.setSignType("RSA2");
        config.setConnectTimeout(5000);
        config.setReadTimeout(10000);
        return new DefaultAlipayClient(config);
    }

    static String readKey(String keyPath) throws Exception {
        return new String(Files.readAllBytes(Paths.get(require(keyPath, "key path"))), StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "");
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Alipay " + name + " required");
        }
        return value;
    }
}

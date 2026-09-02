package com.foodlife.trade.infrastructure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "food.mq")
public class TradeRocketMqProperties {

    private Boolean enabled = false;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup = "food-trade-service-producer";
    private Integer retryDelaySeconds = 30;
    private Integer retryLimit = 50;
}


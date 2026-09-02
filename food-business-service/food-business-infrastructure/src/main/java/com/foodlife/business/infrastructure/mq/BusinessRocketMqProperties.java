package com.foodlife.business.infrastructure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "food.mq")
public class BusinessRocketMqProperties {

    private Boolean enabled = false;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup = "food-business-service-producer";
    private String reviewConsumerGroup = "food-business-review-consumer";
}


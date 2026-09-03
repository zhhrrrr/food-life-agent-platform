package com.foodlife.business.infrastructure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "food.mq")
public class BusinessRabbitMqProperties {

    private Boolean enabled = false;
    private String reviewCreatedQueue = "food.business.review.created.queue";
    private String packageStockEventQueue = "food.business.package.stock.event.queue";
}

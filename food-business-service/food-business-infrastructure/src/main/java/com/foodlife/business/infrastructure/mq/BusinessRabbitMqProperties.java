package com.foodlife.business.infrastructure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "food.mq")
public class BusinessRabbitMqProperties {

    private Boolean enabled = true;
    private String reviewCreatedQueue = "food.business.review.created.queue";
    private String packageStockEventQueue = "food.business.package.stock.event.queue";
    private String deadLetterExchange = "food.business.dlx";
    private String reviewCreatedDeadLetterQueue = "food.business.review.created.dlq";
    private String packageStockEventDeadLetterQueue = "food.business.package.stock.event.dlq";
    private Integer backlogAlertThreshold = 1000;
    private Integer retryDelaySeconds = 30;
    private Integer retryLimit = 50;
    private Integer processingTimeoutSeconds = 120;
}

package com.foodlife.trade.infrastructure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "food.mq")
public class TradeRabbitMqProperties {

    private Boolean enabled = false;
    private Integer retryDelaySeconds = 30;
    private Integer retryLimit = 50;
    private Long orderTimeoutDelayMillis = 30 * 60 * 1000L;
    private String tradeOrderEventQueue = "food.trade.order.event.queue";
    private String paymentEventQueue = "food.trade.payment.event.queue";
    private String orderTimeoutCloseQueue = "food.trade.order.timeout.close.queue";
    private String orderTimeoutDelayQueue = "food.trade.order.timeout.delay.queue";
    private String orderTimeoutDelayRoutingKey = "order.cancel.timeout.delay";
}

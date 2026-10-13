package com.foodlife.trade.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class TradeEventSinkListener {

    private static final Logger log = LoggerFactory.getLogger(TradeEventSinkListener.class);
    private static final String UNKNOWN = "unknown";

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TradeRabbitMqProperties properties;

    public TradeEventSinkListener(ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  TradeRabbitMqProperties properties) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @RabbitListener(queues = "${food.mq.trade-order-event-queue:food.trade.order.event.queue}")
    public void consumeTradeOrderEvent(Message message) {
        consumeEvent(message, properties.getTradeOrderEventQueue(), "trade_order");
    }

    @RabbitListener(queues = "${food.mq.payment-event-queue:food.trade.payment.event.queue}")
    public void consumePaymentEvent(Message message) {
        consumeEvent(message, properties.getPaymentEventQueue(), "payment");
    }

    private void consumeEvent(Message message, String queueName, String eventGroup) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String tag = UNKNOWN;
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(body);
            tag = readText(root, "tag", UNKNOWN);
            String key = readText(root, "key", null);
            String eventId = readText(root, "eventId", message.getMessageProperties().getMessageId());
            if (!StringUtils.hasText(tag) || UNKNOWN.equals(tag) || !StringUtils.hasText(key)) {
                log.warn("skip invalid trade event, queue={}, messageId={}", queueName,
                        message.getMessageProperties().getMessageId());
                return;
            }
            log.info("consume trade event sink success, group={}, tag={}, key={}, eventId={}",
                    eventGroup, tag, key, eventId);
        } catch (Exception e) {
            log.warn("ignore invalid trade event, queue={}, messageId={}, reason={}", queueName,
                    message.getMessageProperties().getMessageId(), e.getMessage());
        } finally {
            sample.stop(Timer.builder("food_mq_consume_duration")
                    .tag("service", "food-trade-service")
                    .tag("queue", queueName)
                    .tag("event", tag)
                    .register(meterRegistry));
        }
    }

    private String readText(JsonNode root, String fieldName, String defaultValue) {
        if (root == null || !root.hasNonNull(fieldName)) {
            return defaultValue;
        }
        String value = root.get(fieldName).asText();
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}

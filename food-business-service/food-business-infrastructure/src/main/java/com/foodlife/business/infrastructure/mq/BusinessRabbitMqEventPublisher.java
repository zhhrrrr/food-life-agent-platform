package com.foodlife.business.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.event.BusinessMqTopics;
import com.foodlife.business.domain.event.IBusinessEventPublisher;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BusinessRabbitMqEventPublisher implements IBusinessEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BusinessRabbitMqEventPublisher.class);

    private final BusinessRabbitMqProperties properties;
    private final ObjectMapper objectMapper;
    private final IShopReviewRepository shopReviewRepository;
    private final RabbitTemplate rabbitTemplate;

    public BusinessRabbitMqEventPublisher(BusinessRabbitMqProperties properties,
                                          ObjectMapper objectMapper,
                                          IShopReviewRepository shopReviewRepository,
                                          RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.shopReviewRepository = shopReviewRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String topic, String tag, String key, Object payload) {
        validate(topic, tag, key);
        String eventId = buildEventId(topic, tag, key);
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("business RabbitMQ mock publish, eventId={}", eventId);
            fallbackIfNeeded(topic, tag, key, eventId);
            return;
        }
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setMessageId(eventId);
            messageProperties.setHeader("eventKey", key);
            messageProperties.setHeader("eventType", tag);
            rabbitTemplate.send(topic, tag,
                    new Message(buildContent(eventId, topic, tag, key, payload).getBytes(StandardCharsets.UTF_8), messageProperties));
            log.info("business RabbitMQ publish success, eventId={}", eventId);
        } catch (Exception e) {
            log.warn("business RabbitMQ publish failed, eventId={}, reason={}", eventId, e.getMessage());
            fallbackIfNeeded(topic, tag, key, eventId);
        }
    }

    private String buildContent(String eventId, String topic, String tag, String key, Object payload) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("eventId", eventId);
            content.put("topic", topic);
            content.put("tag", tag);
            content.put("key", key);
            content.put("payload", payload);
            content.put("eventTime", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("build business event message failed", e);
        }
    }

    private void fallbackIfNeeded(String topic, String tag, String key, String eventId) {
        if (BusinessMqTopics.SHOP_REVIEW_TOPIC.equals(topic) && BusinessMqTopics.REVIEW_CREATED.equals(tag)) {
            shopReviewRepository.applyReviewCreatedStats(key, eventId);
        }
    }

    private String buildEventId(String topic, String tag, String key) {
        return topic + ":" + tag + ":" + key;
    }

    private void validate(String topic, String tag, String key) {
        if (isBlank(topic) || isBlank(tag) || isBlank(key)) {
            throw new IllegalArgumentException("mq topic, routingKey and key required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

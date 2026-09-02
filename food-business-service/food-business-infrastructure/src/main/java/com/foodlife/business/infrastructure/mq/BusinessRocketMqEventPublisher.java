package com.foodlife.business.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.event.BusinessMqTopics;
import com.foodlife.business.domain.event.IBusinessEventPublisher;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BusinessRocketMqEventPublisher implements IBusinessEventPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BusinessRocketMqEventPublisher.class);

    private final BusinessRocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final IShopReviewRepository shopReviewRepository;
    private DefaultMQProducer producer;

    public BusinessRocketMqEventPublisher(BusinessRocketMqProperties properties,
                                          ObjectMapper objectMapper,
                                          IShopReviewRepository shopReviewRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.shopReviewRepository = shopReviewRepository;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("business RocketMQ disabled, event publish uses local fallback mode");
            return;
        }
        producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.start();
        log.info("business RocketMQ producer started, nameServer={}, group={}", properties.getNameServer(), properties.getProducerGroup());
    }

    @Override
    public void publish(String topic, String tag, String key, Object payload) {
        validate(topic, tag, key);
        String eventId = buildEventId(topic, tag, key);
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("business RocketMQ mock publish, eventId={}", eventId);
            fallbackIfNeeded(topic, tag, key, eventId);
            return;
        }
        try {
            Message message = new Message(topic, tag, key, buildContent(eventId, topic, tag, key, payload).getBytes(StandardCharsets.UTF_8));
            SendResult sendResult = producer.send(message);
            log.info("business RocketMQ publish success, eventId={}, sendStatus={}, msgId={}",
                    eventId, sendResult.getSendStatus(), sendResult.getMsgId());
        } catch (Exception e) {
            log.warn("business RocketMQ publish failed, eventId={}, reason={}", eventId, e.getMessage());
            fallbackIfNeeded(topic, tag, key, eventId);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
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
            throw new IllegalArgumentException("mq topic, tag and key required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}


package com.foodlife.business.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.event.BusinessMqTopics;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class BusinessReviewCreatedConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BusinessReviewCreatedConsumer.class);

    private final BusinessRocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final IShopReviewRepository shopReviewRepository;
    private DefaultMQPushConsumer consumer;

    public BusinessReviewCreatedConsumer(BusinessRocketMqProperties properties,
                                         ObjectMapper objectMapper,
                                         IShopReviewRepository shopReviewRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.shopReviewRepository = shopReviewRepository;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("business RocketMQ review consumer disabled");
            return;
        }
        consumer = new DefaultMQPushConsumer(properties.getReviewConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(BusinessMqTopics.SHOP_REVIEW_TOPIC, BusinessMqTopics.REVIEW_CREATED);
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
                for (MessageExt message : messages) {
                    if (!consumeReviewCreated(message)) {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        log.info("business RocketMQ review consumer started, topic={}, group={}",
                BusinessMqTopics.SHOP_REVIEW_TOPIC, properties.getReviewConsumerGroup());
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    private boolean consumeReviewCreated(MessageExt message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(body);
            String reviewNo = root.get("key").asText();
            String eventId = root.hasNonNull("eventId") ? root.get("eventId").asText() : message.getMsgId();
            shopReviewRepository.applyReviewCreatedStats(reviewNo, eventId);
            return true;
        } catch (Exception e) {
            log.warn("consume review.created failed, msgId={}, reason={}", message.getMsgId(), e.getMessage());
            return false;
        }
    }
}


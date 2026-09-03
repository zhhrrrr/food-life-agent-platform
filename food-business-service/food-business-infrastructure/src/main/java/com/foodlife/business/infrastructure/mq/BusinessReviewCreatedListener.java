package com.foodlife.business.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class BusinessReviewCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(BusinessReviewCreatedListener.class);

    private final ObjectMapper objectMapper;
    private final IShopReviewRepository shopReviewRepository;

    public BusinessReviewCreatedListener(ObjectMapper objectMapper,
                                         IShopReviewRepository shopReviewRepository) {
        this.objectMapper = objectMapper;
        this.shopReviewRepository = shopReviewRepository;
    }

    @RabbitListener(queues = "${food.mq.review-created-queue:food.business.review.created.queue}")
    public void consumeReviewCreated(Message message) throws Exception {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);
        String reviewNo = root.get("key").asText();
        String eventId = root.hasNonNull("eventId")
                ? root.get("eventId").asText()
                : message.getMessageProperties().getMessageId();
        shopReviewRepository.applyReviewCreatedStats(reviewNo, eventId);
        log.info("consume review.created success, reviewNo={}, eventId={}", reviewNo, eventId);
    }
}

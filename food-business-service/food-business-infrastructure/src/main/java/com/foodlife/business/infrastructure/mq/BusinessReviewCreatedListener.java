package com.foodlife.business.infrastructure.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.review.repository.IShopReviewRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final MeterRegistry meterRegistry;

    public BusinessReviewCreatedListener(ObjectMapper objectMapper,
                                         IShopReviewRepository shopReviewRepository,
                                         MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.shopReviewRepository = shopReviewRepository;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = "${food.mq.review-created-queue:food.business.review.created.queue}")
    public void consumeReviewCreated(Message message) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(body);
            String reviewNo = root.get("key").asText();
            String eventId = root.hasNonNull("eventId")
                    ? root.get("eventId").asText()
                    : message.getMessageProperties().getMessageId();
            shopReviewRepository.applyReviewCreatedStats(reviewNo, eventId);
            log.info("consume review.created success, reviewNo={}, eventId={}", reviewNo, eventId);
        } finally {
            sample.stop(Timer.builder("food_mq_consume_duration")
                    .tag("service", "food-business-service")
                    .tag("queue", "food.business.review.created.queue")
                    .tag("event", "review.created")
                    .register(meterRegistry));
        }
    }
}

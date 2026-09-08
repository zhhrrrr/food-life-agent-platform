package com.foodlife.business.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.business.domain.event.IBusinessEventPublisher;
import com.foodlife.business.infrastructure.dao.IBusinessLocalMessageMapper;
import com.foodlife.business.infrastructure.dao.po.BusinessLocalMessagePO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BusinessRabbitMqEventPublisher implements IBusinessEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BusinessRabbitMqEventPublisher.class);
    private static final String BIZ_TYPE = "BUSINESS_EVENT";
    private static final String INIT = "INIT";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;

    private final BusinessRabbitMqProperties properties;
    private final IBusinessLocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public BusinessRabbitMqEventPublisher(BusinessRabbitMqProperties properties,
                                          IBusinessLocalMessageMapper localMessageMapper,
                                          ObjectMapper objectMapper,
                                          RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.localMessageMapper = localMessageMapper;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String topic, String tag, String key, Object payload) {
        validate(topic, tag, key);
        String messageId = buildEventId(topic, tag, key);
        BusinessLocalMessagePO message = findByMessageId(messageId);
        if (message == null) {
            message = saveInitMessage(messageId, topic, tag, key, payload);
        }
        publishAfterCommit(message.getMessageId());
    }

    @Override
    public int retryPendingEvents(Integer limit) {
        recoverProcessingMessages();
        int normalizedLimit = normalizeLimit(limit);
        List<BusinessLocalMessagePO> messages = localMessageMapper.selectList(new LambdaQueryWrapper<BusinessLocalMessagePO>()
                .eq(BusinessLocalMessagePO::getBizType, BIZ_TYPE)
                .eq(BusinessLocalMessagePO::getMessageStatus, INIT)
                .le(BusinessLocalMessagePO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(BusinessLocalMessagePO::getId)
                .last("limit " + normalizedLimit));

        int successCount = 0;
        for (BusinessLocalMessagePO message : messages) {
            if (publishStoredMessage(message)) {
                successCount++;
            }
        }
        return successCount;
    }

    private void publishAfterCommit(String messageId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishStoredMessage(findByMessageId(messageId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishStoredMessage(findByMessageId(messageId));
            }
        });
    }

    private boolean publishStoredMessage(BusinessLocalMessagePO message) {
        if (message == null || SUCCESS.equals(message.getMessageStatus())) {
            return true;
        }
        if (!INIT.equals(message.getMessageStatus())) {
            return false;
        }
        if (!markProcessing(message.getId())) {
            return false;
        }
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            markRetryOrFailed(message, "business RabbitMQ publisher disabled");
            return false;
        }
        try {
            JsonNode content = objectMapper.readTree(message.getContent());
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setMessageId(message.getMessageId());
            messageProperties.setHeader("eventKey", content.get("key").asText());
            messageProperties.setHeader("eventType", content.get("tag").asText());
            rabbitTemplate.send(content.get("topic").asText(), content.get("tag").asText(),
                    new Message(message.getContent().getBytes(StandardCharsets.UTF_8), messageProperties));
            markSuccess(message.getId());
            log.info("business RabbitMQ publish success, messageId={}", message.getMessageId());
            return true;
        } catch (Exception e) {
            markRetryOrFailed(message, e.getMessage());
            log.warn("business RabbitMQ publish failed, messageId={}, reason={}", message.getMessageId(), e.getMessage());
            return false;
        }
    }

    private BusinessLocalMessagePO saveInitMessage(String messageId, String topic, String tag, String key, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        BusinessLocalMessagePO po = new BusinessLocalMessagePO();
        po.setMessageId(messageId);
        po.setMessageType(tag);
        po.setBizType(BIZ_TYPE);
        po.setBizId(key);
        po.setMessageStatus(INIT);
        po.setRetryCount(0);
        po.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        po.setNextRetryTime(now);
        po.setContent(buildContent(messageId, topic, tag, key, payload));
        po.setCreateTime(now);
        po.setUpdateTime(now);
        try {
            localMessageMapper.insert(po);
            return po;
        } catch (DuplicateKeyException e) {
            return findByMessageId(messageId);
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

    private BusinessLocalMessagePO findByMessageId(String messageId) {
        return localMessageMapper.selectOne(new LambdaQueryWrapper<BusinessLocalMessagePO>()
                .eq(BusinessLocalMessagePO::getMessageId, messageId)
                .last("limit 1"));
    }

    private boolean markProcessing(Long id) {
        int updated = localMessageMapper.update(null, new LambdaUpdateWrapper<BusinessLocalMessagePO>()
                .set(BusinessLocalMessagePO::getMessageStatus, PROCESSING)
                .set(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(BusinessLocalMessagePO::getId, id)
                .eq(BusinessLocalMessagePO::getMessageStatus, INIT));
        return updated > 0;
    }

    private void markSuccess(Long id) {
        localMessageMapper.update(null, new LambdaUpdateWrapper<BusinessLocalMessagePO>()
                .set(BusinessLocalMessagePO::getMessageStatus, SUCCESS)
                .set(BusinessLocalMessagePO::getFailReason, null)
                .set(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(BusinessLocalMessagePO::getId, id));
    }

    private void markRetryOrFailed(BusinessLocalMessagePO message, String failReason) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int maxRetryCount = message.getMaxRetryCount() == null ? DEFAULT_MAX_RETRY_COUNT : message.getMaxRetryCount();
        if (retryCount >= maxRetryCount) {
            localMessageMapper.update(null, new LambdaUpdateWrapper<BusinessLocalMessagePO>()
                    .set(BusinessLocalMessagePO::getMessageStatus, FAILED)
                    .set(BusinessLocalMessagePO::getFailReason, limitText(failReason))
                    .set(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now())
                    .eq(BusinessLocalMessagePO::getId, message.getId()));
            return;
        }
        localMessageMapper.update(null, new LambdaUpdateWrapper<BusinessLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(BusinessLocalMessagePO::getMessageStatus, INIT)
                .set(BusinessLocalMessagePO::getFailReason, limitText(failReason))
                .set(BusinessLocalMessagePO::getNextRetryTime, LocalDateTime.now().plusSeconds(properties.getRetryDelaySeconds()))
                .set(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(BusinessLocalMessagePO::getId, message.getId()));
    }

    private void recoverProcessingMessages() {
        localMessageMapper.update(null, new LambdaUpdateWrapper<BusinessLocalMessagePO>()
                .set(BusinessLocalMessagePO::getMessageStatus, INIT)
                .set(BusinessLocalMessagePO::getNextRetryTime, LocalDateTime.now())
                .set(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(BusinessLocalMessagePO::getBizType, BIZ_TYPE)
                .eq(BusinessLocalMessagePO::getMessageStatus, PROCESSING)
                .le(BusinessLocalMessagePO::getUpdateTime, LocalDateTime.now().minusSeconds(properties.getProcessingTimeoutSeconds())));
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return properties.getRetryLimit();
        }
        return Math.min(limit, properties.getRetryLimit());
    }

    private String limitText(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}

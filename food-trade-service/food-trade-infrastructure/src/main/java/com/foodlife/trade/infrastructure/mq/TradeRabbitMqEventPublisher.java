package com.foodlife.trade.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.dao.po.TradeLocalMessagePO;
import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TradeRabbitMqEventPublisher implements ITradeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeRabbitMqEventPublisher.class);
    private static final String BIZ_TYPE = "TRADE_EVENT";
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;

    private final TradeRabbitMqProperties properties;
    private final ITradeLocalMessageMapper tradeLocalMessageMapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public TradeRabbitMqEventPublisher(TradeRabbitMqProperties properties,
                                       ITradeLocalMessageMapper tradeLocalMessageMapper,
                                       ObjectMapper objectMapper,
                                       RabbitTemplate rabbitTemplate) {
        this.properties = properties;
        this.tradeLocalMessageMapper = tradeLocalMessageMapper;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String topic, String tag, String key, Object payload) {
        validate(topic, tag, key);
        String messageId = buildMessageId(topic, tag, key);
        TradeLocalMessagePO message = findByMessageId(messageId);
        if (message == null) {
            message = saveInitMessage(messageId, topic, tag, key, payload, null);
        }
        publishStoredMessage(message);
    }

    @Override
    public void publishDelay(String topic, String tag, String key, Object payload) {
        validate(topic, tag, key);
        String messageId = buildMessageId(topic, tag, key);
        TradeLocalMessagePO message = findByMessageId(messageId);
        if (message == null) {
            message = saveInitMessage(messageId, topic, tag, key, payload, properties.getOrderTimeoutDelayMillis());
        }
        publishStoredMessage(message);
    }

    @Override
    public int retryPendingEvents(Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<TradeLocalMessagePO> messages = tradeLocalMessageMapper.selectList(new LambdaQueryWrapper<TradeLocalMessagePO>()
                .eq(TradeLocalMessagePO::getBizType, BIZ_TYPE)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .le(TradeLocalMessagePO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(TradeLocalMessagePO::getId)
                .last("limit " + normalizedLimit));

        int successCount = 0;
        for (TradeLocalMessagePO message : messages) {
            if (publishStoredMessage(message)) {
                successCount++;
            }
        }
        return successCount;
    }

    private boolean publishStoredMessage(TradeLocalMessagePO message) {
        if (message == null || LocalMessageStatusConstants.SUCCESS.equals(message.getMessageStatus())) {
            return true;
        }
        if (!LocalMessageStatusConstants.INIT.equals(message.getMessageStatus())) {
            return false;
        }
        if (!markProcessing(message.getId())) {
            return false;
        }
        try {
            if (!Boolean.TRUE.equals(properties.getEnabled())) {
                log.info("trade RabbitMQ mock publish, messageId={}, type={}", message.getMessageId(), message.getMessageType());
                markSuccess(message.getId());
                return true;
            }
            JsonNode content = objectMapper.readTree(message.getContent());
            sendRabbitMessage(message, content);
            markSuccess(message.getId());
            log.info("trade RabbitMQ publish success, messageId={}", message.getMessageId());
            return true;
        } catch (Exception e) {
            markRetryOrFailed(message, e.getMessage());
            log.warn("trade RabbitMQ publish failed, messageId={}, reason={}", message.getMessageId(), e.getMessage());
            return false;
        }
    }

    private void sendRabbitMessage(TradeLocalMessagePO message, JsonNode content) {
        String exchange = content.get("topic").asText();
        String routingKey = content.get("tag").asText();
        long delayMillis = content.hasNonNull("delayMillis") ? content.get("delayMillis").asLong(0L) : 0L;
        if (delayMillis > 0) {
            routingKey = content.hasNonNull("delayRoutingKey")
                    ? content.get("delayRoutingKey").asText()
                    : properties.getOrderTimeoutDelayRoutingKey();
        }
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setMessageId(message.getMessageId());
        messageProperties.setHeader("eventKey", content.get("key").asText());
        messageProperties.setHeader("eventType", content.get("tag").asText());
        if (delayMillis > 0) {
            messageProperties.setExpiration(String.valueOf(delayMillis));
        }
        rabbitTemplate.send(exchange, routingKey,
                new Message(message.getContent().getBytes(StandardCharsets.UTF_8), messageProperties));
    }

    private TradeLocalMessagePO saveInitMessage(String messageId, String topic, String tag, String key, Object payload, Long delayMillis) {
        LocalDateTime now = LocalDateTime.now();
        TradeLocalMessagePO po = new TradeLocalMessagePO();
        po.setMessageId(messageId);
        po.setMessageType(tag);
        po.setBizType(BIZ_TYPE);
        po.setBizId(key);
        po.setMessageStatus(LocalMessageStatusConstants.INIT);
        po.setRetryCount(0);
        po.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        po.setNextRetryTime(now);
        po.setContent(buildContent(topic, tag, key, payload, delayMillis));
        po.setCreateTime(now);
        po.setUpdateTime(now);
        tradeLocalMessageMapper.insert(po);
        return po;
    }

    private String buildContent(String topic, String tag, String key, Object payload, Long delayMillis) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("topic", topic);
            content.put("tag", tag);
            content.put("key", key);
            content.put("payload", payload);
            if (delayMillis != null && delayMillis > 0) {
                content.put("delayMillis", delayMillis);
                content.put("delayRoutingKey", properties.getOrderTimeoutDelayRoutingKey());
            }
            content.put("eventTime", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("build trade event message failed", e);
        }
    }

    private TradeLocalMessagePO findByMessageId(String messageId) {
        return tradeLocalMessageMapper.selectOne(new LambdaQueryWrapper<TradeLocalMessagePO>()
                .eq(TradeLocalMessagePO::getMessageId, messageId)
                .last("limit 1"));
    }

    private boolean markProcessing(Long id) {
        int updated = tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.PROCESSING)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, id)
                .eq(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT));
        return updated > 0;
    }

    private void markSuccess(Long id) {
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.SUCCESS)
                .set(TradeLocalMessagePO::getFailReason, null)
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, id));
    }

    private void markRetryOrFailed(TradeLocalMessagePO message, String failReason) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int maxRetryCount = message.getMaxRetryCount() == null ? DEFAULT_MAX_RETRY_COUNT : message.getMaxRetryCount();
        if (retryCount >= maxRetryCount) {
            tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                    .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.FAILED)
                    .set(TradeLocalMessagePO::getFailReason, limitText(failReason))
                    .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                    .eq(TradeLocalMessagePO::getId, message.getId()));
            return;
        }
        tradeLocalMessageMapper.update(null, new LambdaUpdateWrapper<TradeLocalMessagePO>()
                .setSql("retry_count = retry_count + 1")
                .set(TradeLocalMessagePO::getMessageStatus, LocalMessageStatusConstants.INIT)
                .set(TradeLocalMessagePO::getFailReason, limitText(failReason))
                .set(TradeLocalMessagePO::getNextRetryTime, LocalDateTime.now().plusSeconds(properties.getRetryDelaySeconds()))
                .set(TradeLocalMessagePO::getUpdateTime, LocalDateTime.now())
                .eq(TradeLocalMessagePO::getId, message.getId()));
    }

    private void validate(String topic, String tag, String key) {
        if (isBlank(topic) || isBlank(tag) || isBlank(key)) {
            throw new IllegalArgumentException("mq topic, tag and key required");
        }
    }

    private String buildMessageId(String topic, String tag, String key) {
        return topic + ":" + tag + ":" + key;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return properties.getRetryLimit();
        }
        return Math.min(limit, properties.getRetryLimit());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String limitText(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}

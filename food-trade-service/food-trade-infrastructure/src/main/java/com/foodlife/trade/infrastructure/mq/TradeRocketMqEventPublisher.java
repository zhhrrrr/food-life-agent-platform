package com.foodlife.trade.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.trade.domain.order.event.ITradeEventPublisher;
import com.foodlife.trade.domain.order.message.constant.LocalMessageStatusConstants;
import com.foodlife.trade.infrastructure.dao.ITradeLocalMessageMapper;
import com.foodlife.trade.infrastructure.dao.po.TradeLocalMessagePO;
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
import java.util.List;
import java.util.Map;

@Component
public class TradeRocketMqEventPublisher implements ITradeEventPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TradeRocketMqEventPublisher.class);
    private static final String BIZ_TYPE = "TRADE_EVENT";
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;

    private final TradeRocketMqProperties properties;
    private final ITradeLocalMessageMapper tradeLocalMessageMapper;
    private final ObjectMapper objectMapper;
    private DefaultMQProducer producer;

    public TradeRocketMqEventPublisher(TradeRocketMqProperties properties,
                                       ITradeLocalMessageMapper tradeLocalMessageMapper,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.tradeLocalMessageMapper = tradeLocalMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("trade RocketMQ disabled, event publish uses local mock-success mode");
            return;
        }
        producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.start();
        log.info("trade RocketMQ producer started, nameServer={}, group={}", properties.getNameServer(), properties.getProducerGroup());
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
            message = saveInitMessage(messageId, topic, tag, key, payload, properties.getOrderTimeoutDelayLevel());
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

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
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
                log.info("trade RocketMQ mock publish, messageId={}, type={}", message.getMessageId(), message.getMessageType());
                markSuccess(message.getId());
                return true;
            }
            JsonNode content = objectMapper.readTree(message.getContent());
            Message rocketMessage = new Message(
                    content.get("topic").asText(),
                    content.get("tag").asText(),
                    content.get("key").asText(),
                    message.getContent().getBytes(StandardCharsets.UTF_8)
            );
            JsonNode delayLevel = content.get("delayLevel");
            if (delayLevel != null && delayLevel.asInt(0) > 0) {
                rocketMessage.setDelayTimeLevel(delayLevel.asInt());
            }
            SendResult sendResult = producer.send(rocketMessage);
            markSuccess(message.getId());
            log.info("trade RocketMQ publish success, messageId={}, sendStatus={}, msgId={}",
                    message.getMessageId(), sendResult.getSendStatus(), sendResult.getMsgId());
            return true;
        } catch (Exception e) {
            markRetryOrFailed(message, e.getMessage());
            log.warn("trade RocketMQ publish failed, messageId={}, reason={}", message.getMessageId(), e.getMessage());
            return false;
        }
    }

    private TradeLocalMessagePO saveInitMessage(String messageId, String topic, String tag, String key, Object payload, Integer delayLevel) {
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
        po.setContent(buildContent(topic, tag, key, payload, delayLevel));
        po.setCreateTime(now);
        po.setUpdateTime(now);
        tradeLocalMessageMapper.insert(po);
        return po;
    }

    private String buildContent(String topic, String tag, String key, Object payload, Integer delayLevel) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("topic", topic);
            content.put("tag", tag);
            content.put("key", key);
            content.put("payload", payload);
            if (delayLevel != null && delayLevel > 0) {
                content.put("delayLevel", delayLevel);
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

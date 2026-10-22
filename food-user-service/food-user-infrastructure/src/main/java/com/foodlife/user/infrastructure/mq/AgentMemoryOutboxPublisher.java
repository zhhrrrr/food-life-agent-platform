package com.foodlife.user.infrastructure.mq;

import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.UserMemoryLocalMessagePO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AgentMemoryOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(AgentMemoryOutboxPublisher.class);
    private final IAgentMemoryMapper mapper;
    private final AgentMemoryMqProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate deliveryTransaction;

    public AgentMemoryOutboxPublisher(IAgentMemoryMapper mapper, AgentMemoryMqProperties properties,
                                      RabbitTemplate rabbitTemplate, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.deliveryTransaction = new TransactionTemplate(transactionManager);
        this.deliveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishAfterCommit(String messageId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(mapper.findOutbox(messageId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    deliveryTransaction.executeWithoutResult(status -> publish(mapper.findOutbox(messageId)));
                } catch (RuntimeException exception) {
                    // The memory write committed; its persisted intent remains available for retry.
                    log.error("post-commit memory delivery needs retry, messageId={}", messageId, exception);
                }
            }
        });
    }

    public int retryPending() {
        mapper.recoverOutbox(LocalDateTime.now().minusMinutes(5));
        List<UserMemoryLocalMessagePO> messages = mapper.findPendingOutbox(properties.getRetryLimit());
        int success = 0;
        for (UserMemoryLocalMessagePO message : messages) if (publish(message)) success++;
        return success;
    }

    private boolean publish(UserMemoryLocalMessagePO message) {
        if (message == null || "SUCCESS".equals(message.getMessageStatus())) return true;
        if (!properties.isEnabled() || mapper.markOutboxProcessing(message.getId()) == 0) return false;
        try {
            MessageProperties headers = new MessageProperties();
            headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            headers.setContentEncoding(StandardCharsets.UTF_8.name());
            headers.setMessageId(message.getMessageId());
            headers.setHeader("eventType", message.getEventType());
            CorrelationData correlation = new CorrelationData(message.getMessageId());
            rabbitTemplate.send(properties.getExchange(), properties.getRoutingKey(),
                    new Message(message.getContent().getBytes(StandardCharsets.UTF_8), headers), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null)
                throw new IllegalStateException("RabbitMQ did not confirm routed delivery");
            mapper.markOutboxSuccess(message.getId());
            return true;
        } catch (Exception exception) {
            int retries = message.getRetryCount() == null ? 0 : message.getRetryCount();
            int maxRetries = message.getMaxRetryCount() == null ? 8 : message.getMaxRetryCount();
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            mapper.markOutboxFailed(message.getId(), retries + 1 >= maxRetries ? "FAILED" : "INIT",
                    LocalDateTime.now().plusSeconds(properties.getRetryDelaySeconds()),
                    reason.substring(0, Math.min(reason.length(), 512)));
            log.warn("agent memory publish failed, messageId={}, reason={}", message.getMessageId(), reason);
            return false;
        }
    }
}

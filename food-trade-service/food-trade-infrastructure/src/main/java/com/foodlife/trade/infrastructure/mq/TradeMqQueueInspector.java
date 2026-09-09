package com.foodlife.trade.infrastructure.mq;

import com.foodlife.trade.domain.order.message.model.MqQueueBacklogEntity;
import com.foodlife.trade.domain.order.message.repository.IMqQueueInspector;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "food.mq", name = "enabled", havingValue = "true")
public class TradeMqQueueInspector implements IMqQueueInspector {

    private final RabbitAdmin rabbitAdmin;
    private final TradeRabbitMqProperties properties;

    public TradeMqQueueInspector(RabbitAdmin rabbitAdmin, TradeRabbitMqProperties properties) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
    }

    @Override
    public List<MqQueueBacklogEntity> listTradeQueues() {
        return Arrays.asList(
                        properties.getTradeOrderEventQueue(),
                        properties.getPaymentEventQueue(),
                        properties.getOrderTimeoutCloseQueue(),
                        properties.getTradeOrderEventDeadLetterQueue(),
                        properties.getPaymentEventDeadLetterQueue(),
                        properties.getOrderTimeoutCloseDeadLetterQueue()
                )
                .stream()
                .map(this::inspect)
                .collect(Collectors.toList());
    }

    private MqQueueBacklogEntity inspect(String queueName) {
        QueueInformation information = rabbitAdmin.getQueueInfo(queueName);
        int messageCount = information == null ? 0 : information.getMessageCount();
        int consumerCount = information == null ? 0 : information.getConsumerCount();
        int threshold = properties.getBacklogAlertThreshold() == null ? 1000 : properties.getBacklogAlertThreshold();
        MqQueueBacklogEntity entity = new MqQueueBacklogEntity();
        entity.setQueueName(queueName);
        entity.setMessageCount(messageCount);
        entity.setConsumerCount(consumerCount);
        entity.setBacklogThreshold(threshold);
        entity.setBacklogAlarm(messageCount >= threshold);
        return entity;
    }
}
